import {Card} from '@/components/ui/Card';
import {formatNumber, formatPercentage} from '@/lib/utils';
import {useTranslation} from 'react-i18next';
import type {UserStatsResponse} from '@/types/session.types';

interface StatsCardsProps {
    stats: UserStatsResponse;
}

export function StatsCards({stats}: StatsCardsProps) {
    const {t} = useTranslation();
    const items = [
        {label: t('stats:totalReviews'), value: formatNumber(stats.totalReviews), icon: '📝', color: 'text-indigo-600'},
        {
            label: t('stats:accuracyRate'),
            value: formatPercentage(stats.globalAccuracyRate),
            icon: '🎯',
            color: 'text-emerald-600'
        },
        {label: t('stats:totalSessions'), value: formatNumber(stats.totalSessions), icon: '📅', color: 'text-amber-600'},
        {
            label: t('stats:cardsReviewed'),
            value: formatNumber(stats.totalCardsReviewed),
            icon: '🃏',
            color: 'text-purple-600'
        },
    ];

    return (
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            {items.map((item) => (
                <Card key={item.label} className="text-center">
                    <span className="text-3xl block mb-2" role="img" aria-hidden="true">{item.icon}</span>
                    <p className={`text-2xl font-bold ${item.color}`}>{item.value}</p>
                    <p className="text-sm text-slate-500 mt-1">{item.label}</p>
                </Card>
            ))}
        </div>
    );
}
