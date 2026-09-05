import apiClient from './client';
import type {UpdateUserRequest, UserResponse} from '@/types/user.types';

export async function getMe(): Promise<UserResponse> {
    const response = await apiClient.get<UserResponse>('/users/me');
    return response.data;
}

export async function updateMe(data: UpdateUserRequest): Promise<UserResponse> {
    const response = await apiClient.patch<UserResponse>('/users/me', data);
    return response.data;
}

export async function deleteMe(): Promise<void> {
    await apiClient.delete('/users/me');
}
