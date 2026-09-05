import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {type ReactNode, useState} from 'react';
import {toast} from 'sonner';
import i18n from '@/i18n/config';
import {getLocalizedErrorMessage} from '@/lib/errors';

function handleGlobalError(error: unknown) {
    const message = getLocalizedErrorMessage(error, i18n.t);
    toast.error(message);
}

export function QueryProvider({children}: { children: ReactNode }) {
    const [queryClient] = useState(
        () =>
            new QueryClient({
                defaultOptions: {
                    queries: {
                        retry: 1,
                        staleTime: 30_000,
                        refetchOnWindowFocus: false,
                    },
                    mutations: {
                        onError: handleGlobalError,
                    },
                },
            })
    );

    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}
