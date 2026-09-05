import {Button} from '@/components/ui/Button';

interface EmptyStateProps {
    icon?: string;
    title: string;
    description: string;
    actionLabel?: string;
    onAction?: () => void;
}

export function EmptyState({icon = '📭', title, description, actionLabel, onAction}: EmptyStateProps) {
    return (
        <div className="flex flex-col items-center justify-center py-16 text-center">
      <span className="text-5xl mb-4" role="img" aria-hidden="true">
        {icon}
      </span>
            <h3 className="text-lg font-semibold text-slate-900 mb-2">{title}</h3>
            <p className="text-sm text-slate-500 max-w-sm mb-6">{description}</p>
            {actionLabel && onAction && (
                <Button onClick={onAction}>{actionLabel}</Button>
            )}
        </div>
    );
}
