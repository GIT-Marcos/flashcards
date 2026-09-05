import apiClient from './client';
import type {AiGenerationResponse, AiTopicRequest} from '@/types/ai.types';

export async function generateCardsFromFile(
    deckId: number,
    file: File,
    provider: string,
    model?: string,
): Promise<AiGenerationResponse> {
    const fd = new FormData();
    fd.append('file', file);
    fd.append('provider', provider);
    if (model) fd.append('model', model);
    const {data} = await apiClient.post<AiGenerationResponse>(`/cards/ai/deck/${deckId}`, fd, {
        headers: {'Content-Type': undefined},
    });
    return data;
}

export async function generateDeckFromFile(
    file: File,
    deckName: string,
    provider: string,
    model?: string,
): Promise<AiGenerationResponse> {
    const fd = new FormData();
    fd.append('file', file);
    fd.append('provider', provider);
    fd.append('deckName', deckName);
    if (model) fd.append('model', model);
    const {data} = await apiClient.post<AiGenerationResponse>('/decks/ai', fd, {
        headers: {'Content-Type': undefined},
    });
    return data;
}

export async function generateDeckFromTopic(req: AiTopicRequest): Promise<AiGenerationResponse> {
    const {data} = await apiClient.post<AiGenerationResponse>('/decks/ai/topic', req);
    return data;
}
