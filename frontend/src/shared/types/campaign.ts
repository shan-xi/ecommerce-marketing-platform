import type {AudienceRules, Page} from './audience';

export type CampaignStatus = 'DRAFT' | 'ACTIVE' | 'PAUSED' | 'ENDED';
export type CampaignAudience = { segmentId: string | null; rules: AudienceRules | null };
export type CampaignWrite = {
    name: string; description: string | null; startsAt: string; endsAt: string;
    landingPage: { headline: string; body: string };
    // Omit rules entirely when campaign-specific rules are disabled. Explicit JSON null
    // is parsed by the backend as a NullNode and fails rule-document validation.
    audience: { segmentId: string | null; rules?: AudienceRules }
};
export type CampaignSummary = {
    id: string; name: string; description: string | null; status: CampaignStatus;
    startsAt: string; endsAt: string; publishedAt: string | null; createdAt: string; updatedAt: string
};
export type Campaign = CampaignSummary & {
    landingPage: { headline: string; body: string }; audience: CampaignAudience
};
export type CampaignPage = Page<CampaignSummary>;
