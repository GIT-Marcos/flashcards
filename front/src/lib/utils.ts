const LOCALE_MAP: Record<string, string> = {
    en: 'en-US',
    es: 'es-ES',
};

let _locale = 'en-US';

export function setLocale(locale: string): void {
    _locale = LOCALE_MAP[locale] || locale || 'en-US';
}

export function formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString(_locale, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
    });
}

export function formatDateTime(iso: string): string {
    return new Date(iso).toLocaleString(_locale, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
    });
}

export function formatDuration(seconds: number, t: (key: string, options?: Record<string, unknown>) => string): string {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    if (h > 0) return `${t('duration:hours', {count: h})} ${t('duration:minutes', {count: m})}`;
    if (m > 0) return `${t('duration:minutes', {count: m})} ${t('duration:seconds', {count: s})}`;
    return t('duration:seconds', {count: s});
}

export function formatNumber(value: number): string {
    return new Intl.NumberFormat(_locale).format(value);
}

export function formatPercentage(value: number): string {
    return new Intl.NumberFormat(_locale, {
        style: 'percent',
        minimumFractionDigits: 1,
        maximumFractionDigits: 1,
    }).format(value);
}

export function getTimeZone(): string {
    return Intl.DateTimeFormat().resolvedOptions().timeZone;
}

export function truncateText(text: string, maxLength: number): string {
    if (text.length <= maxLength) return text;
    return text.slice(0, maxLength) + '…';
}

export function cn(...classes: (string | boolean | undefined | null)[]): string {
    return classes.filter(Boolean).join(' ');
}

export function sleep(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
}
