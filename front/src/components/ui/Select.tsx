import {forwardRef, type SelectHTMLAttributes} from 'react';
import {cn} from '@/lib/utils';

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
    label?: string;
    error?: string;
    options: Array<{ value: string | number; label: string }>;
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(
    ({label, error, options, className, id, ...props}, ref) => {
        const selectId = id || label?.toLowerCase().replace(/\s+/g, '-');
        return (
            <div className="w-full">
                {label && (
                    <label htmlFor={selectId} className="block text-sm font-medium text-slate-700 mb-1">
                        {label}
                    </label>
                )}
                <select
                    ref={ref}
                    id={selectId}
                    className={cn(
                        'w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 transition-colors focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 disabled:bg-slate-50',
                        error && 'border-red-500',
                        className
                    )}
                    aria-invalid={!!error}
                    {...props}
                >
                    {options.map((opt) => (
                        <option key={opt.value} value={opt.value}>
                            {opt.label}
                        </option>
                    ))}
                </select>
                {error && (
                    <p className="mt-1 text-xs text-red-600" role="alert">
                        {error}
                    </p>
                )}
            </div>
        );
    }
);

Select.displayName = 'Select';
