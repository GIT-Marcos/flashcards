import {Toaster} from 'sonner';
import {AuthProvider} from '@/providers/AuthProvider';
import {QueryProvider} from '@/providers/QueryProvider';
import {AppRouter} from '@/routes';

export default function App() {
    return (
        <QueryProvider>
            <AuthProvider>
                <AppRouter/>
                <Toaster
                    position="top-right"
                    richColors
                    closeButton
                    toastOptions={{
                        duration: 4000,
                    }}
                />
            </AuthProvider>
        </QueryProvider>
    );
}
