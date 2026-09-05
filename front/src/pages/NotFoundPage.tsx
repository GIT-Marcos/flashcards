import {useNavigate} from 'react-router-dom';
import {Button} from '@/components/ui/Button';
import {useTranslation} from 'react-i18next';

export function NotFoundPage() {
    const {t} = useTranslation();
    const navigate = useNavigate();

    return (
        <div className="min-h-screen flex items-center justify-center bg-slate-50 p-4">
            <div className="text-center">
        <span className="text-7xl block mb-4" role="img" aria-hidden="true">
          🔍
        </span>
                <h1 className="text-6xl font-bold text-slate-900 mb-2">404</h1>
                <p className="text-xl text-slate-500 mb-8">{t('common:pageNotFound')}</p>
                <Button size="lg" onClick={() => navigate('/decks')}>
                    {t('common:goToDecks')}
                </Button>
            </div>
        </div>
    );
}
