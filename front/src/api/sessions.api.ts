import apiClient from './client';
import type {PaginationParams, Window} from '@/types/api.types';
import type {SessionResponse, UserStatsResponse} from '@/types/session.types';

export async function getSessions(params?: PaginationParams): Promise<Window<SessionResponse>> {
    const response = await apiClient.get<Window<SessionResponse>>('/sessions', {params});
    return response.data;
}

export async function getStats(): Promise<UserStatsResponse> {
    const response = await apiClient.get<UserStatsResponse>('/sessions/stats');
    return response.data;
}
