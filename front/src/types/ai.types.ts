import type {CardResponse} from './card.types';
import type {DeckResponse} from './deck.types';

export type AiProvider = 'OPENAI' | 'ANTHROPIC' | 'GOOGLE' | 'MISTRAL' | 'OPENROUTER';

export interface AiGenerationResponse {
    deck: DeckResponse;
    cards: CardResponse[];
    totalGenerated: number;
    totalSkipped: number;
}

export interface AiTopicRequest {
    prompt: string;
    provider: AiProvider;
    deckName: string;
    model?: string;
}

export interface CreateApiKeyRequest {
    provider: AiProvider;
    apiKey: string;
}

export interface ApiKeyResponse {
    id: number;
    provider: AiProvider;
    keyAlias: string;
    createdAt: string;
}
