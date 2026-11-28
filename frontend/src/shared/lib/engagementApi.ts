import {apiRequest} from './authApi';

export type LandingCampaign = {
    id: string;
    name: string;
    description: string | null;
    status: 'ACTIVE' | 'PAUSED' | 'ENDED';
    startsAt: string;
    endsAt: string;
    landingPage: { headline: string; body: string };
    claimable: boolean;
    memberState?: { eligible: boolean } | null;
};
export type PublicCoupon = {
    id: string;
    name: string;
    description: string | null;
    discountType: 'FIXED_AMOUNT' | 'PERCENTAGE';
    discountValue: number;
    minimumOrderAmount: number | null;
    totalQuantity: number;
    claimedQuantity: number;
    remainingQuantity: number;
    startsAt: string;
    endsAt: string;
    memberStatus: 'AVAILABLE' | 'UNAVAILABLE' | 'ALREADY_CLAIMED' | 'NOT_ELIGIBLE';
    unavailableReason: string | null;
};
export type MemberMessage = {
    id: string;
    campaignId: string;
    campaignName: string;
    title: string;
    body: string;
    deliveredAt: string;
};
export type Page<T> = { data: T[]; page: { number: number; size: number; totalElements: number; totalPages: number } };

export async function getPublicCampaign(campaignId: string) {
    return (await apiRequest<{ data: LandingCampaign }>(`/api/v1/campaigns/${campaignId}`)).data;
}

export async function getPublicCoupons(campaignId: string) {
    return apiRequest<Page<PublicCoupon> & {
        memberState?: { eligible: boolean }
    }>(`/api/v1/campaigns/${campaignId}/coupons?page=0&size=100`);
}

export async function claimCampaignCoupon(campaignId: string, couponId: string, idempotencyKey: string) {
    return apiRequest<{
        data: { claimId: string; campaignId: string; couponId: string; status: 'CLAIMED'; claimedAt: string }
    }>(
        `/api/v1/campaigns/${campaignId}/coupons/${couponId}/claims`, {
            method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({idempotencyKey}),
        });
}

export async function recordCampaignEvent(campaignId: string, eventType: 'IMPRESSION' | 'CLICK' | 'LANDING_PAGE_VIEW', messageId?: string) {
    return apiRequest<void>(`/api/v1/campaigns/${campaignId}/events`, {
        method: 'POST', headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({
            eventId: crypto.randomUUID(),
            eventType, ...(messageId ? {messageId} : {}),
            occurredAt: new Date().toISOString()
        }),
        ...(eventType === 'CLICK' ? {keepalive: true} : {}),
    });
}

export async function getMemberInbox() {
    return apiRequest<Page<MemberMessage>>('/api/v1/member/messages?page=0&size=50');
}
