import {cn} from '@/lib/utils';

interface BadgeProps {
    variant?: 'default' | 'success' | 'warning' | 'danger';
    children: React.ReactNode;
    className?: string;
}

const badgeVariants = {
    default: 'bg-slate-100 text-slate-700',
    success: 'bg-emerald-100 text-emerald-700',
    warning: 'bg-amber-100 text-amber-700',
    danger: 'bg-red-100 text-red-700',
};

export function Badge({variant = 'default', children, className}: BadgeProps) {
    return (
        <span
            className={cn(
                'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium',
                badgeVariants[variant],
                className
            )}
        >
      {children}
    </span>
    );
}
