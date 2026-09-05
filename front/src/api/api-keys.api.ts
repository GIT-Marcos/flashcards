import apiClient from './client';
import type {ApiKeyResponse, CreateApiKeyRequest} from '@/types/ai.types';

export async function getApiKeys(): Promise<ApiKeyResponse[]> {
    const {data} = await apiClient.get<ApiKeyResponse[]>('/users/me/api-keys');
    return data;
}

export async function createApiKey(req: CreateApiKeyRequest): Promise<ApiKeyResponse> {
    const {data} = await apiClient.post<ApiKeyResponse>('/users/me/api-keys', req);
    return data;
}

export async function deleteApiKey(keyId: number): Promise<void> {
    await apiClient.delete(`/users/me/api-keys/${keyId}`);
}
