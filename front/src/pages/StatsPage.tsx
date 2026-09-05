import {useQuery} from '@tanstack/react-query';
import {getStats} from '@/api/sessions.api';
import {StatsCards} from '@/components/stats/StatsCards';
import {QualityDistributionChart} from '@/components/stats/QualityDistributionChart';
import {SessionsList} from '@/components/stats/SessionsList';
import {SkeletonCard} from '@/components/ui/Skeleton';
import {ErrorState} from '@/components/shared/ErrorState';
import {useTranslation} from 'react-i18next';

export function StatsPage() {
    const {t} = useTranslation();
    const {data: stats, isLoading, isError, refetch} = useQuery({
        queryKey: ['sessions', 'stats'],
        queryFn: getStats,
    });

    return (
        <div>
            <div className="mb-8">
                <h1 className="text-2xl font-bold text-slate-900">{t('stats:title')}</h1>
                <p className="text-sm text-slate-500 mt-1">{t('stats:trackProgress')}</p>
            </div>

            {isLoading && (
                <div className="space-y-6">
                    <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                        {Array.from({length: 4}).map((_, i) => (
                            <SkeletonCard key={i}/>
                        ))}
                    </div>
                    <SkeletonCard/>
                </div>
            )}

            {isError && <ErrorState onRetry={() => refetch()}/>}

            {stats && (
                <div className="space-y-8">
                    <StatsCards stats={stats}/>
                    <QualityDistributionChart distribution={stats.qualityDistribution}/>
                    <SessionsList/>
                </div>
            )}
        </div>
    );
}
