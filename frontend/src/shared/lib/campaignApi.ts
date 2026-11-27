import {apiRequest} from './authApi';
import type {Campaign, CampaignPage, CampaignStatus, CampaignWrite} from '../types/campaign';
import type {AudiencePreview} from '../types/audience';

export const listCampaigns = (page = 0, size = 20, status?: CampaignStatus | '') => {
    const query = new URLSearchParams({page: String(page), size: String(size)});
    if (status) query.set('status', status);
    return apiRequest<CampaignPage>(`/api/v1/admin/campaigns?${query.toString()}`);
};
export const getCampaign = async (id: string) => (await apiRequest<{
    data: Campaign
}>(`/api/v1/admin/campaigns/${id}`)).data;
export const createCampaign = async (input: CampaignWrite) => (await apiRequest<{
    data: Campaign
}>('/api/v1/admin/campaigns', {
    method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(input)
})).data;
export const updateCampaign = async (id: string, input: CampaignWrite) => (await apiRequest<{
    data: Campaign
}>(`/api/v1/admin/campaigns/${id}`, {
    method: 'PUT', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(input)
})).data;
export const previewCampaignAudience = async (id: string) => (await apiRequest<{
    data: AudiencePreview
}>(`/api/v1/admin/campaigns/${id}/audience-preview`, {
    method: 'POST', headers: {'Content-Type': 'application/json'}, body: '{}'
})).data;
