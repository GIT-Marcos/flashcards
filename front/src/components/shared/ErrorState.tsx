import {Button} from '@/components/ui/Button';
import {useTranslation} from 'react-i18next';

interface ErrorStateProps {
    message?: string;
    onRetry?: () => void;
}

export function ErrorState({message, onRetry}: ErrorStateProps) {
    const {t} = useTranslation();

    return (
        <div className="flex flex-col items-center justify-center py-16 text-center">
      <span className="text-5xl mb-4" role="img" aria-hidden="true">
        ⚠️
      </span>
            <h3 className="text-lg font-semibold text-slate-900 mb-2">{t('error:title')}</h3>
            <p className="text-sm text-slate-500 max-w-sm mb-6">{message || t('error:somethingWentWrong')}</p>
            {onRetry && (
                <Button variant="secondary" onClick={onRetry}>
                    {t('error:tryAgain')}
                </Button>
            )}
        </div>
    );
}
