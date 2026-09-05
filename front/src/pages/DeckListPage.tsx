import {useNavigate} from 'react-router-dom';
import {useInfiniteQuery} from '@tanstack/react-query';
import {getDecks} from '@/api/decks.api';
import {DeckCard} from '@/components/shared/DeckCard';
import {PaginationLoader} from '@/components/shared/PaginationLoader';
import {EmptyState} from '@/components/shared/EmptyState';
import {ErrorState} from '@/components/shared/ErrorState';
import {SkeletonCard} from '@/components/ui/Skeleton';
import {Button} from '@/components/ui/Button';
import {DEFAULT_PAGE_SIZE} from '@/lib/constants';
import {useTranslation} from 'react-i18next';
import type {DeckResponse} from '@/types/deck.types';
import type {PaginationParams} from '@/types/api.types';

export function DeckListPage() {
    const {t} = useTranslation();
    const navigate = useNavigate();

    const {
        data,
        isLoading,
        isError,
        fetchNextPage,
        hasNextPage,
        isFetchingNextPage,
        isFetching,
        refetch,
    } = useInfiniteQuery({
        queryKey: ['decks', 'all'],
        queryFn: ({pageParam}) => getDecks(pageParam),
        initialPageParam: {pageSize: DEFAULT_PAGE_SIZE, direction: 'DESC'} as PaginationParams,
        getNextPageParam: (lastPage) => {
            if (!lastPage.hasNext) return undefined;
            const content = lastPage.content;
            if (content.length === 0) return undefined;
            const last = content[content.length - 1] as DeckResponse;
            return {
                lastId: last.id,
                cursorValue: last.createdAt,
                pageSize: DEFAULT_PAGE_SIZE,
                direction: 'DESC',
            } as PaginationParams;
        },
    });

    const allDecks = data?.pages.flatMap((page) => page.content) ?? [];

    return (
        <div>
            <div className="flex items-center justify-between mb-8">
                <div>
                    <h1 className="text-2xl font-bold text-slate-900">{t('decks:myDecks')}</h1>
                    <p className="text-sm text-slate-500 mt-1">{t('decks:manageAndStudy')}</p>
                </div>
                <Button onClick={() => navigate('/decks/new')}>
                    {t('decks:newDeck')}
                </Button>
            </div>

            {/* All Decks Section */}
            <section>
                <h2 className="text-lg font-semibold text-slate-900 mb-4">{t('decks:allDecks')}</h2>

                {isLoading && (
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                        {Array.from({length: 6}).map((_, i) => (
                            <SkeletonCard key={i}/>
                        ))}
                    </div>
                )}

                {isError && !data && <ErrorState onRetry={() => refetch()}/>}

                {!isLoading && !isError && allDecks.length === 0 && (
                    <EmptyState
                        icon="📚"
                        title={t('empty:noDecksYet')}
                        description={t('empty:noDecksDesc')}
                        actionLabel={t('empty:createFirstDeck')}
                        onAction={() => navigate('/decks/new')}
                    />
                )}

                {!isLoading && allDecks.length > 0 && (
                    <>
                        {isFetching && !isLoading && !isFetchingNextPage && (
                            <div className="flex justify-center mb-4">
                                <div className="h-1 w-24 bg-indigo-200 rounded-full overflow-hidden">
                                    <div className="h-full w-full bg-indigo-500 animate-pulse"/>
                                </div>
                            </div>
                        )}
                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                            {allDecks.map((deck) => (
                                <DeckCard key={deck.id} deck={deck}/>
                            ))}
                        </div>
                        <PaginationLoader
                            hasNext={hasNextPage}
                            isLoading={isFetchingNextPage}
                            onLoadMore={() => fetchNextPage()}
                        />
                    </>
                )}
            </section>
        </div>
    );
}
