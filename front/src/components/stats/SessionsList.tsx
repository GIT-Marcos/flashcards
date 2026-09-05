import {useInfiniteQuery} from '@tanstack/react-query';
import {getSessions} from '@/api/sessions.api';
import {Card} from '@/components/ui/Card';
import {PaginationLoader} from '@/components/shared/PaginationLoader';
import {SkeletonList} from '@/components/ui/Skeleton';
import {EmptyState} from '@/components/shared/EmptyState';
import {ErrorState} from '@/components/shared/ErrorState';
import {formatDateTime, formatDuration, formatPercentage} from '@/lib/utils';
import {DEFAULT_PAGE_SIZE} from '@/lib/constants';
import {useTranslation} from 'react-i18next';
import type {SessionResponse} from '@/types/session.types';
import type {PaginationParams} from '@/types/api.types';

export function SessionsList() {
    const {t} = useTranslation();
    const {
        data,
        isLoading,
        isError,
        refetch,
        fetchNextPage,
        hasNextPage,
        isFetchingNextPage,
    } = useInfiniteQuery({
        queryKey: ['sessions'],
        queryFn: ({pageParam}) => getSessions(pageParam),
        initialPageParam: {pageSize: DEFAULT_PAGE_SIZE, direction: 'DESC'} as PaginationParams,
        getNextPageParam: (lastPage) => {
            if (!lastPage.hasNext) return undefined;
            const content = lastPage.content;
            if (content.length === 0) return undefined;
            const last = content[content.length - 1] as SessionResponse;
            return {
                lastId: last.id,
                cursorValue: last.startTime,
                pageSize: DEFAULT_PAGE_SIZE,
                direction: 'DESC',
            } as PaginationParams;
        },
    });

    const sessions = data?.pages.flatMap((page) => page.content) ?? [];

    if (isLoading) return <SkeletonList count={3}/>;
    if (isError && !data) return <ErrorState onRetry={() => refetch()}/>;
    if (sessions.length === 0) {
        return (
            <EmptyState
                icon="📖"
                title={t('stats:noSessionsYet')}
                description={t('stats:noSessionsDesc')}
            />
        );
    }

    return (
        <div>
            <h3 className="text-lg font-semibold text-slate-900 mb-4">{t('stats:recentSessions')}</h3>
            <div className="space-y-3">
                {sessions.map((session) => (
                    <Card key={session.id}>
                        <div className="flex items-center justify-between flex-wrap gap-2">
                            <div>
                                <p className="font-medium text-slate-900">{formatDateTime(session.startTime)}</p>
                                <p className="text-sm text-slate-500">
                                    {t('stats:duration', {duration: formatDuration(session.durationSeconds, t)})}
                                </p>
                            </div>
                            <div className="flex items-center gap-4 text-sm">
                <span className="text-slate-600">
                    <span className="font-semibold">{session.cardsReviewed}</span> {t('stats:cards')}
                </span>
                                <span className="text-emerald-600 font-semibold">
                  {formatPercentage(session.accuracyRate)}
                </span>
                            </div>
                        </div>
                    </Card>
                ))}
            </div>
            <PaginationLoader hasNext={hasNextPage} isLoading={isFetchingNextPage} onLoadMore={() => fetchNextPage()}/>
        </div>
    );
}
