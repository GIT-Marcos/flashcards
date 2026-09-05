import {useMemo} from 'react';
import {getTimeZone} from '@/lib/utils';

export function useTimeZone() {
    const timeZone = useMemo(() => getTimeZone(), []);
    return timeZone;
}
