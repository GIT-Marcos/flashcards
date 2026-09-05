import {cn} from '@/lib/utils';

interface SkeletonProps {
    className?: string;
}

export function Skeleton({className}: SkeletonProps) {
    return <div className={cn('animate-pulse rounded-lg bg-slate-200', className)}/>;
}

export function SkeletonCard() {
    return (
        <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <Skeleton className="h-5 w-3/4 mb-3"/>
            <Skeleton className="h-4 w-1/2 mb-2"/>
            <Skeleton className="h-4 w-1/3"/>
        </div>
    );
}

export function SkeletonList({count = 5}: { count?: number }) {
    return (
        <div className="space-y-3">
            {Array.from({length: count}).map((_, i) => (
                <SkeletonCard key={i}/>
            ))}
        </div>
    );
}
