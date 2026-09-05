import {Button} from '@/components/ui/Button';
import {useTranslation} from 'react-i18next';

interface PaginationLoaderProps {
    hasNext: boolean;
    isLoading: boolean;
    onLoadMore: () => void;
}

export function PaginationLoader({hasNext, isLoading, onLoadMore}: PaginationLoaderProps) {
    const {t} = useTranslation();
    if (!hasNext) return null;

    return (
        <div className="flex justify-center py-6">
            <Button variant="secondary" onClick={onLoadMore} isLoading={isLoading}>
                {t('common:loadMore')}
            </Button>
        </div>
    );
}
