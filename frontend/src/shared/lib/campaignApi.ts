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

export type CampaignMessage = { id: string; campaignId: string; title: string; body: string; createdAt: string; updatedAt: string };
export type DeliveryStatus = { status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'; totalCount: number; processedCount: number; cursorMemberId: string | null; attemptCount: number; lastError: string | null; retryable: boolean };
export type PublishResult = { campaignId: string; status: 'ACTIVE'; audienceCount: number; deliveryStatus: DeliveryStatus['status']; warningCode: 'NO_AUDIENCE_MEMBERS' | null; publishedAt: string };
export const getCampaignMessage = async (id: string) => (await apiRequest<{data: CampaignMessage}>(`/api/v1/admin/campaigns/${id}/message`)).data;
export const saveCampaignMessage = async (id: string, title: string, body: string) => (await apiRequest<{data: CampaignMessage}>(`/api/v1/admin/campaigns/${id}/message`, {method: 'PUT', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({title, body})})).data;
export const publishCampaign = async (id: string) => (await apiRequest<{data: PublishResult}>(`/api/v1/admin/campaigns/${id}/publish`, {method: 'POST'})).data;
export const pauseCampaign = async (id: string) => apiRequest<{data: {campaignId: string; status: 'PAUSED'}}>(`/api/v1/admin/campaigns/${id}/pause`, {method: 'POST'});
export const getDeliveryStatus = async (id: string) => (await apiRequest<{data: DeliveryStatus}>(`/api/v1/admin/campaigns/${id}/delivery-status`)).data;
export const retryDelivery = async (id: string) => apiRequest<{data: {campaignId: string; status: 'PENDING'}}>(`/api/v1/admin/campaigns/${id}/delivery/retry`, {method: 'POST'});
