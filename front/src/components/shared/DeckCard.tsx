import {useNavigate} from 'react-router-dom';
import {Card} from '@/components/ui/Card';
import {Badge} from '@/components/ui/Badge';
import {Button} from '@/components/ui/Button';
import {formatDate} from '@/lib/utils';
import {useTranslation} from 'react-i18next';
import type {DeckResponse} from '@/types/deck.types';

interface DeckCardProps {
    deck: DeckResponse;
}

export function DeckCard({deck}: DeckCardProps) {
    const {t} = useTranslation();
    const navigate = useNavigate();

    return (
        <Card
            className="cursor-pointer group"
            onClick={() => navigate(`/decks/${deck.id}`)}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    navigate(`/decks/${deck.id}`);
                }
            }}
        >
            <div className="flex items-start justify-between mb-3">
                <h3 className="font-semibold text-slate-900 group-hover:text-indigo-600 transition-colors truncate pr-2">
                    {deck.name}
                </h3>
                {deck.hasPendingCards && (
                    <Badge variant="warning">{t('decks:due')}</Badge>
                )}
            </div>
            <p className="text-xs text-slate-400 mb-4">
                {t('decks:created', {date: formatDate(deck.createdAt)})}
            </p>
            {deck.hasPendingCards && (
                <Button
                    size="sm"
                    onClick={(e) => {
                        e.stopPropagation();
                        navigate(`/decks/${deck.id}/study`);
                    }}
                    className="w-full"
                >
                    {t('decks:studyNow')}
                </Button>
            )}
        </Card>
    );
}
