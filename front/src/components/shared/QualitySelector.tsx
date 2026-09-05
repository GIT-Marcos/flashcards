import {QUALITY_LABELS} from '@/lib/constants';
import {cn} from '@/lib/utils';
import {useTranslation} from 'react-i18next';

interface QualitySelectorProps {
    onSelect: (quality: number) => void;
    disabled?: boolean;
}

export function QualitySelector({onSelect, disabled}: QualitySelectorProps) {
    const {t} = useTranslation();
    return (
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
            {Object.entries(QUALITY_LABELS).map(([quality, {color}]) => (
                <button
                    key={quality}
                    onClick={() => onSelect(Number(quality))}
                    disabled={disabled}
                    className={cn(
                        'rounded-lg px-3 py-3 text-sm font-medium transition-all disabled:opacity-50 disabled:cursor-not-allowed',
                        color
                    )}
                >
                    <span className="block text-lg font-bold mb-1">{quality}</span>
                    <span className="block text-xs opacity-90">{t(`quality:${quality}`)}</span>
                </button>
            ))}
        </div>
    );
}
