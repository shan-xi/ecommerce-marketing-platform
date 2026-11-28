import {apiRequest} from './authApi';
import type {CampaignAnalytics} from '../types/analytics';

export const getCampaignAnalytics = async (campaignId: string) => (await apiRequest<{
    data: CampaignAnalytics
}>(`/api/v1/admin/campaigns/${campaignId}/analytics`)).data;
