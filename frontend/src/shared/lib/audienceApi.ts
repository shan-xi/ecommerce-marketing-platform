import {apiRequest} from './authApi';
import type {AudiencePreview, AudienceRules, AudienceSegment, Page} from '../types/audience';

export type SegmentWrite = { name: string; description: string | null; rules: AudienceRules };
export const listAudienceSegments = (page = 0, size = 20) => apiRequest<Page<AudienceSegment>>(`/api/v1/admin/audience-segments?page=${page}&size=${size}`);
export const getAudienceSegment = async (id: string) => (await apiRequest<{
    data: AudienceSegment
}>(`/api/v1/admin/audience-segments/${id}`)).data;
export const createAudienceSegment = async (input: SegmentWrite) => (await apiRequest<{
    data: AudienceSegment
}>('/api/v1/admin/audience-segments', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(input)
})).data;
export const updateAudienceSegment = async (id: string, input: SegmentWrite) => (await apiRequest<{
    data: AudienceSegment
}>(`/api/v1/admin/audience-segments/${id}`, {
    method: 'PUT',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(input)
})).data;
export const previewAudience = async (rules: AudienceRules) => (await apiRequest<{
    data: AudiencePreview
}>('/api/v1/admin/audience-segments/preview', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(rules)
})).data;
