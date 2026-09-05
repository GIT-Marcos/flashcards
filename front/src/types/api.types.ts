export interface ScrollPosition {
    keys: Record<string, unknown>;
}

export interface Window<T> {
    content: T[];
    hasNext: boolean;
    hasPrevious: boolean;
    first: boolean;
    last: boolean;
    nextScrollPosition: ScrollPosition | null;
    previousScrollPosition: ScrollPosition | null;
}

export interface ProblemDetail {
    type: string;
    title: string;
    status: number;
    detail: string;
    instance?: string;
}

export interface PaginationParams {
    lastId?: number;
    cursorValue?: string;
    pageSize?: number;
    direction?: 'ASC' | 'DESC';
}
