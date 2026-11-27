-- Phase 2: PostgreSQL schema foundation for the approved v1.5.0 database design.
-- Do not edit this migration after it has been applied; use a new versioned migration.

CREATE TABLE users
(
    id            UUID PRIMARY KEY,
    email         VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'MEMBER'))
);

CREATE TABLE members
(
    id              UUID PRIMARY KEY,
    user_id         UUID           NOT NULL,
    display_name    VARCHAR(120)   NOT NULL,
    birth_date      DATE,
    gender          VARCHAR(30),
    registered_at   TIMESTAMPTZ    NOT NULL,
    membership_tier VARCHAR(30)    NOT NULL,
    order_count     INTEGER        NOT NULL DEFAULT 0,
    total_spending  NUMERIC(14, 2) NOT NULL DEFAULT 0,
    last_order_at   TIMESTAMPTZ,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_members_user_id UNIQUE (user_id),
    CONSTRAINT fk_members_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_members_gender CHECK (gender IS NULL OR gender IN ('FEMALE', 'MALE', 'OTHER', 'UNDISCLOSED')),
    CONSTRAINT ck_members_membership_tier CHECK (membership_tier IN ('STANDARD', 'SILVER', 'GOLD', 'PLATINUM')),
    CONSTRAINT ck_members_order_count CHECK (order_count >= 0),
    CONSTRAINT ck_members_total_spending CHECK (total_spending >= 0)
);

CREATE TABLE refresh_token_sessions
(
    id                   UUID PRIMARY KEY,
    token_family_id      UUID         NOT NULL,
    user_id              UUID         NOT NULL,
    token_hash           VARCHAR(255) NOT NULL,
    expires_at           TIMESTAMPTZ  NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL,
    consumed_at          TIMESTAMPTZ,
    revoked_at           TIMESTAMPTZ,
    replaced_by_token_id UUID,
    user_agent           VARCHAR(500),
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT uq_refresh_token_generation_family UNIQUE (id, user_id, token_family_id),
    CONSTRAINT uq_refresh_token_single_predecessor UNIQUE (replaced_by_token_id),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_refresh_token_replacement_same_family
        FOREIGN KEY (replaced_by_token_id, user_id, token_family_id)
            REFERENCES refresh_token_sessions (id, user_id, token_family_id)
);
CREATE INDEX idx_refresh_token_user_family ON refresh_token_sessions (user_id, token_family_id);
CREATE INDEX idx_refresh_token_family_revoked ON refresh_token_sessions (token_family_id, revoked_at);
CREATE INDEX idx_refresh_token_expires_at ON refresh_token_sessions (expires_at);

CREATE TABLE audience_segments
(
    id                 UUID PRIMARY KEY,
    name               VARCHAR(120) NOT NULL,
    description        VARCHAR(500),
    rules              JSONB        NOT NULL,
    created_by_user_id UUID         NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    archived_at        TIMESTAMPTZ,
    CONSTRAINT fk_audience_segments_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id)
);

CREATE TABLE campaigns
(
    id                    UUID PRIMARY KEY,
    name                  VARCHAR(160) NOT NULL,
    description           TEXT,
    landing_page_headline VARCHAR(200) NOT NULL,
    landing_page_body     TEXT         NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    starts_at             TIMESTAMPTZ  NOT NULL,
    ends_at               TIMESTAMPTZ  NOT NULL,
    audience_segment_id   UUID,
    audience_rules        JSONB,
    published_at          TIMESTAMPTZ,
    created_by_user_id    UUID         NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_campaigns_audience_segment FOREIGN KEY (audience_segment_id) REFERENCES audience_segments (id),
    CONSTRAINT fk_campaigns_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT ck_campaigns_status CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED')),
    CONSTRAINT ck_campaigns_time_window CHECK (ends_at > starts_at),
    CONSTRAINT ck_campaigns_audience_definition CHECK (audience_segment_id IS NOT NULL OR audience_rules IS NOT NULL)
);
CREATE INDEX idx_campaigns_status_window ON campaigns (status, starts_at, ends_at);
CREATE INDEX idx_campaigns_created_at ON campaigns (created_at DESC);

CREATE TABLE campaign_audience_members
(
    campaign_id       UUID        NOT NULL,
    member_id         UUID        NOT NULL,
    snapshot_at       TIMESTAMPTZ NOT NULL,
    source_segment_id UUID,
    CONSTRAINT pk_campaign_audience_members PRIMARY KEY (campaign_id, member_id),
    CONSTRAINT fk_campaign_audience_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id),
    CONSTRAINT fk_campaign_audience_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_campaign_audience_source_segment FOREIGN KEY (source_segment_id) REFERENCES audience_segments (id)
);
CREATE INDEX idx_campaign_audience_member_campaign ON campaign_audience_members (member_id, campaign_id);

CREATE TABLE campaign_coupons
(
    id                   UUID PRIMARY KEY,
    campaign_id          UUID           NOT NULL,
    name                 VARCHAR(160)   NOT NULL,
    description          TEXT,
    discount_type        VARCHAR(30)    NOT NULL,
    discount_value       NUMERIC(12, 2) NOT NULL,
    minimum_order_amount NUMERIC(14, 2),
    total_quantity       INTEGER        NOT NULL,
    claimed_quantity     INTEGER        NOT NULL DEFAULT 0,
    starts_at            TIMESTAMPTZ    NOT NULL,
    ends_at              TIMESTAMPTZ    NOT NULL,
    created_at           TIMESTAMPTZ    NOT NULL,
    updated_at           TIMESTAMPTZ    NOT NULL,
    CONSTRAINT fk_campaign_coupons_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id),
    CONSTRAINT uq_campaign_coupon_id UNIQUE (campaign_id, id),
    CONSTRAINT ck_campaign_coupons_discount_type CHECK (discount_type IN ('FIXED_AMOUNT', 'PERCENTAGE')),
    CONSTRAINT ck_campaign_coupons_discount_value CHECK (
        (discount_type = 'FIXED_AMOUNT' AND discount_value > 0)
            OR (discount_type = 'PERCENTAGE' AND discount_value > 0 AND discount_value <= 100)
        ),
    CONSTRAINT ck_campaign_coupons_minimum_order CHECK (minimum_order_amount IS NULL OR minimum_order_amount >= 0),
    CONSTRAINT ck_campaign_coupons_total_quantity CHECK (total_quantity >= 1),
    CONSTRAINT ck_campaign_coupons_claimed_quantity CHECK (claimed_quantity >= 0 AND claimed_quantity <= total_quantity),
    CONSTRAINT ck_campaign_coupons_time_window CHECK (ends_at > starts_at)
);
CREATE INDEX idx_campaign_coupons_campaign ON campaign_coupons (campaign_id);

CREATE TABLE coupon_claims
(
    id                 UUID PRIMARY KEY,
    campaign_id        UUID        NOT NULL,
    campaign_coupon_id UUID        NOT NULL,
    member_id          UUID        NOT NULL,
    idempotency_key    UUID        NOT NULL,
    claimed_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_coupon_claims_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id),
    CONSTRAINT fk_coupon_claims_coupon FOREIGN KEY (campaign_coupon_id) REFERENCES campaign_coupons (id),
    CONSTRAINT fk_coupon_claims_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_claim_campaign_coupon FOREIGN KEY (campaign_id, campaign_coupon_id)
        REFERENCES campaign_coupons (campaign_id, id),
    CONSTRAINT uq_coupon_claim_campaign_coupon_member UNIQUE (campaign_id, campaign_coupon_id, member_id),
    CONSTRAINT uq_coupon_claim_member_idempotency UNIQUE (member_id, idempotency_key)
);
CREATE INDEX idx_coupon_claims_member_claimed_at ON coupon_claims (member_id, claimed_at DESC);
CREATE INDEX idx_coupon_claims_campaign_coupon ON coupon_claims (campaign_id, campaign_coupon_id);

CREATE TABLE campaign_messages
(
    id          UUID PRIMARY KEY,
    campaign_id UUID         NOT NULL,
    title       VARCHAR(160) NOT NULL,
    body        TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_campaign_messages_campaign UNIQUE (campaign_id),
    CONSTRAINT uq_campaign_message_campaign_id UNIQUE (campaign_id, id),
    CONSTRAINT fk_campaign_messages_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id)
);

CREATE TABLE campaign_delivery_jobs
(
    id               UUID PRIMARY KEY,
    campaign_id      UUID        NOT NULL,
    status           VARCHAR(20) NOT NULL,
    cursor_member_id UUID,
    attempt_count    INTEGER     NOT NULL DEFAULT 0,
    last_error       TEXT,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_campaign_delivery_jobs_campaign UNIQUE (campaign_id),
    CONSTRAINT fk_campaign_delivery_jobs_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id),
    CONSTRAINT fk_delivery_cursor_campaign_snapshot_member
        FOREIGN KEY (campaign_id, cursor_member_id)
            REFERENCES campaign_audience_members (campaign_id, member_id),
    CONSTRAINT ck_campaign_delivery_jobs_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_campaign_delivery_jobs_attempt_count CHECK (attempt_count BETWEEN 0 AND 3)
);

CREATE TABLE member_messages
(
    id                  UUID PRIMARY KEY,
    campaign_message_id UUID        NOT NULL,
    campaign_id         UUID        NOT NULL,
    member_id           UUID        NOT NULL,
    delivered_at        TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_member_messages_message FOREIGN KEY (campaign_message_id) REFERENCES campaign_messages (id),
    CONSTRAINT fk_member_messages_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id),
    CONSTRAINT fk_member_messages_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_member_message_campaign_message FOREIGN KEY (campaign_id, campaign_message_id)
        REFERENCES campaign_messages (campaign_id, id),
    CONSTRAINT uq_member_message_campaign_member UNIQUE (campaign_id, member_id),
    CONSTRAINT uq_member_message_campaign_message_member UNIQUE (campaign_id, campaign_message_id, member_id)
);
CREATE INDEX idx_member_messages_member_delivered_at ON member_messages (member_id, delivered_at DESC);
CREATE INDEX idx_member_messages_campaign_message ON member_messages (campaign_id, campaign_message_id);

CREATE TABLE campaign_events
(
    id                  UUID PRIMARY KEY,
    event_id            UUID        NOT NULL,
    campaign_id         UUID        NOT NULL,
    member_id           UUID        NOT NULL,
    campaign_message_id UUID,
    campaign_coupon_id  UUID,
    event_type          VARCHAR(40) NOT NULL,
    occurred_at         TIMESTAMPTZ NOT NULL,
    received_at         TIMESTAMPTZ NOT NULL,
    metadata            JSONB,
    CONSTRAINT fk_campaign_events_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns (id),
    CONSTRAINT fk_campaign_events_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_campaign_event_recipient_message
        FOREIGN KEY (campaign_id, campaign_message_id, member_id)
            REFERENCES member_messages (campaign_id, campaign_message_id, member_id),
    CONSTRAINT fk_campaign_event_campaign_message
        FOREIGN KEY (campaign_id, campaign_message_id)
            REFERENCES campaign_messages (campaign_id, id),
    CONSTRAINT fk_campaign_event_campaign_coupon
        FOREIGN KEY (campaign_id, campaign_coupon_id)
            REFERENCES campaign_coupons (campaign_id, id),
    CONSTRAINT uq_campaign_events_member_event UNIQUE (member_id, event_id),
    CONSTRAINT ck_campaign_events_type CHECK (event_type IN
                                              ('IMPRESSION', 'CLICK', 'LANDING_PAGE_VIEW', 'CLAIM_ATTEMPT')),
    CONSTRAINT ck_campaign_events_context CHECK (
        (event_type IN ('IMPRESSION', 'CLICK') AND campaign_message_id IS NOT NULL AND campaign_coupon_id IS NULL)
            OR (event_type = 'LANDING_PAGE_VIEW' AND campaign_message_id IS NULL AND campaign_coupon_id IS NULL)
            OR (event_type = 'CLAIM_ATTEMPT' AND campaign_message_id IS NULL)
        ),
    CONSTRAINT ck_campaign_events_claim_attempt_outcome CHECK (
        event_type <> 'CLAIM_ATTEMPT'
            OR (
            metadata IS NOT NULL
                AND metadata ? 'outcome'
            AND metadata->>'outcome' IN ('SUCCEEDED', 'FAILED', 'REPLAYED')
            )
        )
);
CREATE INDEX idx_campaign_events_campaign_type_received ON campaign_events (campaign_id, event_type, received_at);
CREATE INDEX idx_campaign_events_campaign_member_type ON campaign_events (campaign_id, member_id, event_type);
CREATE INDEX idx_campaign_events_member_received ON campaign_events (member_id, received_at DESC);
CREATE INDEX idx_campaign_events_campaign_message_member ON campaign_events (campaign_id, campaign_message_id, member_id);
CREATE INDEX idx_campaign_events_campaign_coupon ON campaign_events (campaign_id, campaign_coupon_id);
