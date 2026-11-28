import {apiRequest} from './authApi';

export type MemberProfile = {
    memberId: string;
    displayName: string | null;
    birthDate: string | null;
    age: number | null;
    gender: string | null;
    registeredAt: string;
    membershipTier: string | null;
    orderCount: number;
    totalSpending: number;
    lastOrderAt: string | null;
    lastLoginAt: string | null
};
export type MemberClaim = {
    claimId: string;
    campaignId: string;
    couponId: string;
    campaignName: string;
    couponName: string;
    description: string | null;
    discountType: string;
    discountValue: number;
    minimumOrderAmount: number | null;
    startsAt: string;
    endsAt: string;
    claimedAt: string
};

export async function getMemberProfile() {
    return (await apiRequest<{ data: MemberProfile }>('/api/v1/member/profile')).data;
}

export async function getMemberCoupons() {
    return (await apiRequest<{ data: MemberClaim[] }>('/api/v1/member/coupons')).data;
}
