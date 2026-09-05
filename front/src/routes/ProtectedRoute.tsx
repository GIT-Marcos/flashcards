import {Navigate, Outlet} from 'react-router-dom';
import {useAuth} from '@/hooks/useAuth';
import {useTranslation} from 'react-i18next';

export function ProtectedRoute() {
    const {t} = useTranslation();
    const {isAuthenticated, isLoading} = useAuth();

    if (isLoading) {
        return (
            <div className="min-h-screen flex items-center justify-center bg-slate-50">
                <div className="text-center">
                    <div
                        className="inline-block animate-spin rounded-full h-10 w-10 border-4 border-indigo-200 border-t-indigo-600 mb-4"/>
                    <p className="text-sm text-slate-500">{t('common:loading')}</p>
                </div>
            </div>
        );
    }

    if (!isAuthenticated) {
        return <Navigate to="/login" replace/>;
    }

    return <Outlet/>;
}
