import {forwardRef, type InputHTMLAttributes} from 'react';
import {cn} from '@/lib/utils';

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
    label?: string;
    error?: string;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
    ({label, error, className, id, ...props}, ref) => {
        const inputId = id || label?.toLowerCase().replace(/\s+/g, '-');
        return (
            <div className="w-full">
                {label && (
                    <label htmlFor={inputId} className="block text-sm font-medium text-slate-700 mb-1">
                        {label}
                    </label>
                )}
                <input
                    ref={ref}
                    id={inputId}
                    className={cn(
                        'w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 placeholder-slate-400 transition-colors focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 disabled:bg-slate-50 disabled:text-slate-500',
                        error && 'border-red-500 focus:border-red-500 focus:ring-red-500/20',
                        className
                    )}
                    aria-invalid={!!error}
                    aria-describedby={error ? `${inputId}-error` : undefined}
                    {...props}
                />
                {error && (
                    <p id={`${inputId}-error`} className="mt-1 text-xs text-red-600" role="alert">
                        {error}
                    </p>
                )}
            </div>
        );
    }
);

Input.displayName = 'Input';
