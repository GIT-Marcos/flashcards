export interface CardResponse {
    id: number;
    front: string;
    back: string;
    nextReviewDate: string;
}

export interface CreateCardRequest {
    front: string;
    back: string;
}

export interface UpdateCardRequest {
    front?: string;
    back?: string;
}
