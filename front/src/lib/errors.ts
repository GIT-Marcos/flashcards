import {AxiosError} from 'axios';
import type {TFunction} from 'i18next';
import type {ProblemDetail} from '@/types/api.types';

const ERROR_TITLE_MAP: Record<string, string> = {
    'Expired Token': 'sessionExpired',
    'Invalid Token': 'invalidSession',
    'Invalid Credentials': 'invalidCredentials',
    'Authentication Required': 'authenticationRequired',
    'Authentication Error': 'authenticationError',
    'Access Denied': 'accessDenied',
    'Not Found': 'notFound',
    'Validation error': 'validationError',
    'Missing cookie': 'missingCookie',
    'Business rule violation': 'businessRuleViolation',
    'Too Many Requests': 'tooManyRequests',
    Conflict: 'conflict',
    'Internal error': 'internalError',
};

export function getLocalizedErrorMessage(error: unknown, t: TFunction): string {
    if (!(error instanceof AxiosError)) {
        console.error('[i18n] Non-Axios error:', error);
        return t('error:somethingWentWrong');
    }

    const problem = error.response?.data as ProblemDetail | undefined;
    const status = error.response?.status;

    console.error('[i18n] Backend error:', {
        status,
        title: problem?.title,
        detail: problem?.detail,
        type: problem?.type,
    });

    if (status === 429) {
        return t('toast:tooManyRequests');
    }

    if (problem?.title) {
        const key = ERROR_TITLE_MAP[problem.title];
        if (key) {
            return t(`error:${key}`);
        }
    }

    return t('error:somethingWentWrong');
}
