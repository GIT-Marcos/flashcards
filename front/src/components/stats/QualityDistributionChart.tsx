import {Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis} from 'recharts';
import {Card} from '@/components/ui/Card';
import {QUALITY_LABELS} from '@/lib/constants';
import {useTranslation} from 'react-i18next';

interface QualityDistributionChartProps {
    distribution: Record<number, number>;
}

const COLORS = ['#dc2626', '#ef4444', '#f97316', '#eab308', '#34d399', '#059669'];

export function QualityDistributionChart({distribution}: QualityDistributionChartProps) {
    const {t} = useTranslation();
    const data = Object.entries(QUALITY_LABELS).map(([quality]) => ({
        quality: Number(quality),
        label: `Q${quality}`,
        count: distribution[Number(quality)] || 0,
    }));

    return (
        <Card>
            <h3 className="text-lg font-semibold text-slate-900 mb-4">{t('stats:qualityDistribution')}</h3>
            <ResponsiveContainer width="100%" height={300}>
                <BarChart data={data}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0"/>
                    <XAxis dataKey="label" tick={{fontSize: 12}}/>
                    <YAxis tick={{fontSize: 12}} allowDecimals={false}/>
                    <Tooltip/>
                    <Bar dataKey="count" radius={[4, 4, 0, 0]}>
                        {data.map((_, index) => (
                            <Cell key={`cell-${index}`} fill={COLORS[index]}/>
                        ))}
                    </Bar>
                </BarChart>
            </ResponsiveContainer>
        </Card>
    );
}
