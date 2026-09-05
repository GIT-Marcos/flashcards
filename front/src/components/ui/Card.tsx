import {type HTMLAttributes, type ReactNode} from 'react';
import {cn} from '@/lib/utils';

interface CardProps extends HTMLAttributes<HTMLDivElement> {
    children: ReactNode;
}

export function Card({children, className, ...props}: CardProps) {
    return (
        <div
            className={cn(
                'rounded-xl border border-slate-200 bg-white p-5 shadow-sm transition-shadow hover:shadow-md',
                className
            )}
            {...props}
        >
            {children}
        </div>
    );
}
