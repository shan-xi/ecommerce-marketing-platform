-- Fictional, deterministic local/demo identities. Passwords are BCrypt hashes only.
-- The same synthetic demo hash is used for all seeded accounts; authentication is Phase 3 scope.

INSERT INTO users (id, email, password_hash, role, status, created_at, updated_at)
VALUES ('10000000-0000-4000-8000-000000000001', 'admin@example.test',
        '$2a$10$Bs/3dZfIpkNxhM2YAQkaweV5HymyS3ofo0rebYq1KVdxyFTlr1UjW', 'ADMIN', 'ACTIVE',
        TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2026-01-01 00:00:00+00'),
       ('10000000-0000-4000-8000-000000000002', 'member.one@example.test',
        '$2a$10$Bs/3dZfIpkNxhM2YAQkaweV5HymyS3ofo0rebYq1KVdxyFTlr1UjW', 'MEMBER', 'ACTIVE',
        TIMESTAMPTZ '2026-01-02 00:00:00+00', TIMESTAMPTZ '2026-01-02 00:00:00+00'),
       ('10000000-0000-4000-8000-000000000003', 'member.two@example.test',
        '$2a$10$Bs/3dZfIpkNxhM2YAQkaweV5HymyS3ofo0rebYq1KVdxyFTlr1UjW', 'MEMBER', 'ACTIVE',
        TIMESTAMPTZ '2026-01-03 00:00:00+00', TIMESTAMPTZ '2026-01-03 00:00:00+00'),
       ('10000000-0000-4000-8000-000000000004', 'member.three@example.test',
        '$2a$10$Bs/3dZfIpkNxhM2YAQkaweV5HymyS3ofo0rebYq1KVdxyFTlr1UjW', 'MEMBER', 'ACTIVE',
        TIMESTAMPTZ '2026-01-04 00:00:00+00', TIMESTAMPTZ '2026-01-04 00:00:00+00') ON CONFLICT (id) DO NOTHING;

INSERT INTO members (id, user_id, display_name, birth_date, gender, registered_at, membership_tier,
                     order_count, total_spending, last_order_at, last_login_at, created_at, updated_at)
VALUES ('20000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000002', 'Demo Member One',
        DATE '1992-04-12', 'FEMALE', TIMESTAMPTZ '2024-01-10 08:00:00+00', 'GOLD', 8, 12500.00,
        TIMESTAMPTZ '2026-10-15 03:00:00+00', TIMESTAMPTZ '2026-11-20 02:00:00+00',
        TIMESTAMPTZ '2024-01-10 08:00:00+00', TIMESTAMPTZ '2026-11-20 02:00:00+00'),
       ('20000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000003', 'Demo Member Two',
        DATE '1988-09-23', 'MALE', TIMESTAMPTZ '2023-06-01 08:00:00+00', 'SILVER', 3, 4200.00,
        TIMESTAMPTZ '2026-09-02 05:30:00+00', TIMESTAMPTZ '2026-11-18 01:00:00+00',
        TIMESTAMPTZ '2023-06-01 08:00:00+00', TIMESTAMPTZ '2026-11-18 01:00:00+00'),
       ('20000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000004', 'Demo Member Three',
        DATE '1998-12-05', 'UNDISCLOSED', TIMESTAMPTZ '2025-03-15 08:00:00+00', 'STANDARD', 0, 0.00, NULL, NULL,
        TIMESTAMPTZ '2025-03-15 08:00:00+00', TIMESTAMPTZ '2025-03-15 08:00:00+00') ON CONFLICT (id) DO NOTHING;
