import {Button} from '@/components/ui/Button';
import {Card} from '@/components/ui/Card';
import {useNavigate} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {formatPercentage} from '@/lib/utils';

interface SessionSummaryProps {
    cardsReviewed: number;
    correctCount: number;
    deckId: number;
}

export function SessionSummary({cardsReviewed, correctCount, deckId}: SessionSummaryProps) {
    const {t} = useTranslation();
    const navigate = useNavigate();
    const accuracy = cardsReviewed > 0 ? correctCount / cardsReviewed : 0;

    return (
        <div className="max-w-md mx-auto text-center">
      <span className="text-6xl mb-4 block" role="img" aria-hidden="true">
        🎉
      </span>
            <h2 className="text-2xl font-bold text-slate-900 mb-2">{t('study:deckComplete')}</h2>
            <p className="text-slate-500 mb-8">{t('study:greatJob')}</p>

            <div className="grid grid-cols-2 gap-4 mb-8">
                <Card>
                    <p className="text-3xl font-bold text-indigo-600">{cardsReviewed}</p>
                    <p className="text-sm text-slate-500 mt-1">{t('study:cardsReviewed')}</p>
                </Card>
                <Card>
                    <p className="text-3xl font-bold text-emerald-600">{formatPercentage(accuracy)}</p>
                    <p className="text-sm text-slate-500 mt-1">{t('study:accuracy')}</p>
                </Card>
                <Card>
                    <p className="text-3xl font-bold text-emerald-600">{correctCount}</p>
                    <p className="text-sm text-slate-500 mt-1">{t('study:correct')}</p>
                </Card>
                <Card>
                    <p className="text-3xl font-bold text-red-500">{cardsReviewed - correctCount}</p>
                    <p className="text-sm text-slate-500 mt-1">{t('study:toReviewAgain')}</p>
                </Card>
            </div>

            <div className="flex flex-col gap-3">
                <Button onClick={() => navigate(`/decks/${deckId}`)}>{t('study:backToDeck')}</Button>
                <Button variant="secondary" onClick={() => navigate('/decks')}>
                    {t('study:allDecks')}
                </Button>
                <Button variant="secondary" onClick={() => navigate('/reviews')}>
                    {t('study:goToReviews')}
                </Button>
            </div>
        </div>
    );
}
