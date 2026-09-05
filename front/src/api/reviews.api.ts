import apiClient from './client';
import type {CardResponse} from '@/types/card.types';
import {sleep} from '@/lib/utils';
import {AxiosError} from 'axios';

export async function reviewCard(cardId: number, quality: number): Promise<CardResponse> {
    const maxRetries = 2;
    const baseDelay = 250;

    for (let attempt = 0; attempt <= maxRetries; attempt++) {
        try {
            const response = await apiClient.post<CardResponse>(`/reviews/card/${cardId}`, {quality});
            return response.data;
        } catch (error) {
            if (error instanceof AxiosError && error.response?.status === 409 && attempt < maxRetries) {
                await sleep(baseDelay * Math.pow(2, attempt));
                continue;
            }
            throw error;
        }
    }

    throw new Error('Max retries exceeded');
}
