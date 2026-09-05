import {Button} from '@/components/ui/Button';
import {useTranslation} from 'react-i18next';
import type {CardResponse} from '@/types/card.types';

interface FlashCardProps {
    card: CardResponse;
    onShowAnswer: () => void;
    isRevealed: boolean;
}

export function FlashCard({card, onShowAnswer, isRevealed}: FlashCardProps) {
    const {t} = useTranslation();
    return (
        <div className="max-w-2xl mx-auto">
            <div className="rounded-2xl bg-white border border-slate-200 shadow-lg overflow-hidden">
                {/* Front */}
                <div className="p-8 min-h-[200px] flex items-center justify-center">
                    <p className="text-xl text-slate-900 text-center whitespace-pre-wrap break-words">
                        {card.front}
                    </p>
                </div>

                {/* Divider & Back */}
                {isRevealed && (
                    <>
                        <div className="border-t border-slate-200"/>
                        <div className="p-8 bg-slate-50 min-h-[150px] flex items-center justify-center">
                            <p className="text-lg text-slate-700 text-center whitespace-pre-wrap break-words">
                                {card.back}
                            </p>
                        </div>
                    </>
                )}
            </div>

            {!isRevealed && (
                <div className="flex justify-center mt-6">
                    <Button size="lg" onClick={onShowAnswer}>
                        {t('study:showAnswer')}
                    </Button>
                </div>
            )}
        </div>
    );
}
