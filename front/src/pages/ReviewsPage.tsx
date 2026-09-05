import {useNavigate} from 'react-router-dom';
import {useQuery} from '@tanstack/react-query';
import {getDueDecks} from '@/api/decks.api';
import {DeckCard} from '@/components/shared/DeckCard';
import {EmptyState} from '@/components/shared/EmptyState';
import {ErrorState} from '@/components/shared/ErrorState';
import {SkeletonCard} from '@/components/ui/Skeleton';
import {useTranslation} from 'react-i18next';

export function ReviewsPage() {
    const {t} = useTranslation();
    const navigate = useNavigate();

    const {data, isLoading, isError, refetch} = useQuery({
        queryKey: ['decks', 'due'],
        queryFn: () => getDueDecks({pageSize: 50, direction: 'ASC'}),
    });

    const dueDecks = data?.content || [];

    if (isLoading) {
        return (
            <div>
                <div className="mb-8">
                    <h1 className="text-2xl font-bold text-slate-900">{t('reviews:title')}</h1>
                    <p className="text-sm text-slate-500 mt-1">{t('reviews:decksDueForReview')}</p>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                    {Array.from({length: 6}).map((_, i) => <SkeletonCard key={i}/>)}
                </div>
            </div>
        );
    }

    if (isError) {
        return <ErrorState onRetry={() => refetch()}/>;
    }

    if (dueDecks.length === 0) {
        return (
            <EmptyState
                icon="✅"
                title={t('reviews:allCaughtUp')}
                description={t('reviews:allCaughtUpDesc')}
                actionLabel={t('reviews:browseAllDecks')}
                onAction={() => navigate('/decks')}
            />
        );
    }

    return (
        <div>
            <div className="mb-8">
                <h1 className="text-2xl font-bold text-slate-900">{t('reviews:title')}</h1>
                <p className="text-sm text-slate-500 mt-1">{t('reviews:decksDueForReview')}</p>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                {dueDecks.map((deck) => <DeckCard key={deck.id} deck={deck}/>)}
            </div>
        </div>
    );
}
