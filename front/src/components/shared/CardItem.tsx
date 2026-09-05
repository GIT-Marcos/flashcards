import {Card} from '@/components/ui/Card';
import {formatDateTime, truncateText} from '@/lib/utils';
import {useTranslation} from 'react-i18next';
import type {CardResponse} from '@/types/card.types';

interface CardItemProps {
    card: CardResponse;
    onClick: () => void;
}

export function CardItem({card, onClick}: CardItemProps) {
    const {t} = useTranslation();
    const isPending = new Date(card.nextReviewDate) <= new Date();

    return (
        <Card
            className="cursor-pointer"
            onClick={onClick}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    onClick();
                }
            }}
        >
            <div className="flex items-start justify-between gap-4">
                <div className="min-w-0 flex-1">
                    <p className="font-medium text-slate-900 whitespace-pre-wrap break-words">
                        {truncateText(card.front, 120)}
                    </p>
                    <p className="mt-1 text-sm text-slate-500 whitespace-pre-wrap break-words">
                        {truncateText(card.back, 200)}
                    </p>
                </div>
                <div className="flex-shrink-0 text-right">
          <span className={`text-xs ${isPending ? 'text-amber-600 font-medium' : 'text-slate-400'}`}>
            {isPending ? t('decks:dueNow') : formatDateTime(card.nextReviewDate)}
          </span>
                </div>
            </div>
        </Card>
    );
}
