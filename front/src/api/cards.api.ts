import apiClient from './client';
import type {PaginationParams, Window} from '@/types/api.types';
import type {CardResponse, CreateCardRequest, UpdateCardRequest} from '@/types/card.types';

export async function getCard(cardId: number): Promise<CardResponse> {
    const response = await apiClient.get<CardResponse>(`/cards/${cardId}`);
    return response.data;
}

export async function createCard(deckId: number, data: CreateCardRequest): Promise<CardResponse> {
    const response = await apiClient.post<CardResponse>(`/cards/deck/${deckId}`, data);
    return response.data;
}

export async function updateCard(cardId: number, data: UpdateCardRequest): Promise<CardResponse> {
    const response = await apiClient.patch<CardResponse>(`/cards/${cardId}`, data);
    return response.data;
}

export async function deleteCard(cardId: number): Promise<void> {
    await apiClient.delete(`/cards/${cardId}`);
}

export async function getDeckCards(deckId: number, params?: PaginationParams): Promise<Window<CardResponse>> {
    const response = await apiClient.get<Window<CardResponse>>(`/cards/deck/${deckId}`, {params});
    return response.data;
}

export async function getPendingCards(deckId: number, params?: PaginationParams): Promise<Window<CardResponse>> {
    const response = await apiClient.get<Window<CardResponse>>(`/cards/deck/${deckId}/pending`, {params});
    return response.data;
}
