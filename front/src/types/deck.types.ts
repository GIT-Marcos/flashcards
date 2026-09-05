export interface DeckResponse {
    id: number;
    name: string;
    hasPendingCards: boolean;
    createdAt: string;
    updatedAt: string;
}

export interface CreateDeckRequest {
    name: string;
}

export interface UpdateDeckRequest {
    name: string;
}
