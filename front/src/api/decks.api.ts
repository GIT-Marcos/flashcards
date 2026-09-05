import apiClient from './client';
import type {PaginationParams, Window} from '@/types/api.types';
import type {CreateDeckRequest, DeckResponse, UpdateDeckRequest} from '@/types/deck.types';

export async function getDecks(params?: PaginationParams): Promise<Window<DeckResponse>> {
    const response = await apiClient.get<Window<DeckResponse>>('/decks', {params});
    return response.data;
}

export async function getDueDecks(params?: PaginationParams): Promise<Window<DeckResponse>> {
    const response = await apiClient.get<Window<DeckResponse>>('/decks/due', {params});
    return response.data;
}

export async function createDeck(data: CreateDeckRequest): Promise<DeckResponse> {
    const response = await apiClient.post<DeckResponse>('/decks', data);
    return response.data;
}

export async function updateDeck(deckId: number, data: UpdateDeckRequest): Promise<DeckResponse> {
    const response = await apiClient.patch<DeckResponse>(`/decks/${deckId}`, data);
    return response.data;
}

export async function deleteDeck(deckId: number): Promise<void> {
    await apiClient.delete(`/decks/${deckId}`);
}
