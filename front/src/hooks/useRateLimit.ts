import {useCallback, useEffect, useState} from 'react';

export function useRateLimit() {
    const [retryAfter, setRetryAfter] = useState(0);

    useEffect(() => {
        if (retryAfter <= 0) return;
        const interval = setInterval(() => {
            setRetryAfter((prev) => {
                if (prev <= 1) return 0;
                return prev - 1;
            });
        }, 1000);
        return () => clearInterval(interval);
    }, [retryAfter]);

    const handleRateLimitError = useCallback((error: unknown) => {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const axiosError = error as any;
        if (axiosError?.response?.status === 429) {
            const retryHeader = axiosError.response.headers?.['retry-after'];
            const seconds = parseInt(retryHeader || '30', 10);
            setRetryAfter(seconds);
        }
    }, []);

    return {retryAfter, isRateLimited: retryAfter > 0, handleRateLimitError};
}
