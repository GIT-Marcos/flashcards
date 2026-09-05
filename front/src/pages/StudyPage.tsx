import {useCallback, useEffect, useState} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import {useMutation, useQueryClient} from '@tanstack/react-query';
import {getPendingCards} from '@/api/cards.api';
import {reviewCard} from '@/api/reviews.api';
import {FlashCard} from '@/components/study/FlashCard';
import {QualitySelector} from '@/components/shared/QualitySelector';
import {SessionSummary} from '@/components/study/SessionSummary';
import {EmptyState} from '@/components/shared/EmptyState';
import {Skeleton} from '@/components/ui/Skeleton';
import {toast} from 'sonner';
import {useTranslation} from 'react-i18next';
import {formatDateTime} from '@/lib/utils';
import type {CardResponse} from '@/types/card.types';

export function StudyPage() {
    const {t} = useTranslation();
    const {deckId} = useParams();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const id = Number(deckId);

    const [cards, setCards] = useState<CardResponse[]>([]);
    const [currentIndex, setCurrentIndex] = useState(0);
    const [isRevealed, setIsRevealed] = useState(false);
    const [isComplete, setIsComplete] = useState(false);
    const [correctCount, setCorrectCount] = useState(0);
    const [reviewedCount, setReviewedCount] = useState(0);
    const [lastReviewDate, setLastReviewDate] = useState<string | null>(null);
    const [isLoadingCards, setIsLoadingCards] = useState(true);

    // Load all pending cards by iterating through pages
    const loadAllCards = useCallback(async () => {
        setIsLoadingCards(true);
        const allCards: CardResponse[] = [];
        let lastId: number | undefined;
        let cursorValue: string | undefined;
        let hasMore = true;

        try {
            while (hasMore) {
                const data = await getPendingCards(id, {
                    pageSize: 100,
                    direction: 'ASC',
                    lastId,
                    cursorValue,
                });
                allCards.push(...data.content);
                hasMore = data.hasNext;
                if (data.content.length > 0) {
                    const last = data.content[data.content.length - 1];
                    lastId = last.id;
                    cursorValue = last.nextReviewDate;
                }
            }
            setCards(allCards);
        } catch {
            toast.error(t('study:failedToLoad'));
        } finally {
            setIsLoadingCards(false);
        }
    }, [id]);

    useEffect(() => {
        loadAllCards();
    }, [loadAllCards]);

    const reviewMutation = useMutation({
        mutationFn: ({cardId, quality}: { cardId: number; quality: number }) =>
            reviewCard(cardId, quality),
        onSuccess: (updatedCard, variables) => {
            if (variables.quality >= 3) {
                setCorrectCount((prev) => prev + 1);
            }
            setReviewedCount((prev) => prev + 1);
            setLastReviewDate(updatedCard.nextReviewDate);

            // Move to next card
            if (currentIndex < cards.length - 1) {
                setCurrentIndex((prev) => prev + 1);
                setIsRevealed(false);
            } else {
                setIsComplete(true);
                queryClient.invalidateQueries({queryKey: ['decks']});
                queryClient.invalidateQueries({queryKey: ['cards', id]});
                queryClient.invalidateQueries({queryKey: ['sessions']});
            }
        },
    });

    const handleQualitySelect = (quality: number) => {
        const currentCard = cards[currentIndex];
        if (!currentCard) return;
        reviewMutation.mutate({cardId: currentCard.id, quality});
    };

    if (isLoadingCards) {
        return (
            <div className="max-w-2xl mx-auto">
                <Skeleton className="h-8 w-48 mb-6"/>
                <Skeleton className="h-64 w-full rounded-2xl mb-6"/>
                <Skeleton className="h-12 w-48 mx-auto"/>
            </div>
        );
    }

    if (cards.length === 0) {
        return (
            <EmptyState
                icon="✅"
                title={t('empty:noPendingCards')}
                description={t('empty:noPendingCardsDesc')}
                actionLabel={t('decks:backToDecks')}
                onAction={() => navigate('/decks')}
            />
        );
    }

    if (isComplete) {
        return <SessionSummary cardsReviewed={reviewedCount} correctCount={correctCount} deckId={id}/>;
    }

    const currentCard = cards[currentIndex];

    return (
        <div>
            {/* Header */}
            <div className="flex items-center justify-between mb-8">
                <div className="flex items-center gap-3">
                    <button
                        onClick={() => navigate(`/decks/${id}`)}
                        className="text-slate-400 hover:text-slate-600 transition-colors"
                        aria-label={t('study:backToDeck')}
                    >
                        ←
                    </button>
                    <h1 className="text-xl font-bold text-slate-900">{t('study:studySession')}</h1>
                </div>
                <div className="flex items-center gap-4 text-sm">
          <span className="text-slate-500">
            {t('study:cardOf', {current: currentIndex + 1, total: cards.length})}
          </span>
                    <span className="text-emerald-600 font-semibold">
            {t('study:reviewed', {count: reviewedCount})}
          </span>
                </div>
            </div>

            {/* Progress bar */}
            <div className="w-full bg-slate-200 rounded-full h-2 mb-8">
                <div
                    className="bg-indigo-600 h-2 rounded-full transition-all duration-300"
                    style={{width: `${((currentIndex) / cards.length) * 100}%`}}
                />
            </div>

            {/* Flashcard */}
            <FlashCard
                card={currentCard}
                isRevealed={isRevealed}
                onShowAnswer={() => setIsRevealed(true)}
            />

            {/* Quality buttons */}
            {isRevealed && (
                <div className="max-w-2xl mx-auto mt-6">
                    <p className="text-sm text-slate-500 text-center mb-3">{t('study:howWellDidYouKnow')}</p>
                    <QualitySelector
                        onSelect={handleQualitySelect}
                        disabled={reviewMutation.isPending}
                    />
                    {lastReviewDate && (
                        <p className="text-xs text-slate-400 text-center mt-4">
                            {t('study:nextReview', {date: formatDateTime(lastReviewDate)})}
                        </p>
                    )}
                </div>
            )}
        </div>
    );
}
