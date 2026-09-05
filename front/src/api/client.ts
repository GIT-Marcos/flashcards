import axios, {AxiosError, InternalAxiosRequestConfig} from 'axios';
import {API_URL} from '@/lib/constants';
import {getTimeZone} from '@/lib/utils';
import type {ProblemDetail} from '@/types/api.types';

let accessToken: string | null = null;
let isRefreshing = false;
let failedQueue: Array<{
    resolve: (token: string | null) => void;
    reject: (error: unknown) => void;
}> = [];

export function setAccessToken(token: string | null) {
    accessToken = token;
}

export function getAccessToken(): string | null {
    return accessToken;
}

const processQueue = (error: unknown, token: string | null = null) => {
    failedQueue.forEach((prom) => {
        if (error) {
            prom.reject(error);
        } else {
            prom.resolve(token);
        }
    });
    failedQueue = [];
};

const apiClient = axios.create({
    baseURL: API_URL,
    withCredentials: true,
    headers: {
        'Content-Type': 'application/json',
    },
});

const TIME_ZONE_ENDPOINTS = ['/auth/login', '/auth/refresh-token', '/reviews/', '/auth/signup'];

apiClient.interceptors.request.use(
    (config: InternalAxiosRequestConfig) => {
        if (accessToken && config.headers) {
            config.headers.Authorization = `Bearer ${accessToken}`;
        }

        const url = config.url || '';
        const method = (config.method || '').toUpperCase();

        if (method === 'POST') {
            const needsTimeZone = TIME_ZONE_ENDPOINTS.some((ep) => url.startsWith(ep));
            if (needsTimeZone && config.headers) {
                config.headers['Time-Zone'] = getTimeZone();
            }
        }

        return config;
    },
    (error) => Promise.reject(error)
);

let onAuthFailure: (() => void) | null = null;

export function setOnAuthFailure(callback: () => void) {
    onAuthFailure = callback;
}

apiClient.interceptors.response.use(
    (response) => response,
    async (error: AxiosError<ProblemDetail>) => {
        const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

        if (
            error.response?.status === 401 &&
            !originalRequest._retry &&
            originalRequest.url !== '/auth/login' &&
            originalRequest.url !== '/auth/signup' &&
            originalRequest.url !== '/auth/forgot-password' &&
            originalRequest.url !== '/auth/reset-password' &&
            originalRequest.url !== '/auth/refresh-token'
        ) {
            if (isRefreshing) {
                return new Promise((resolve, reject) => {
                    failedQueue.push({resolve, reject});
                }).then((token) => {
                    if (originalRequest.headers) {
                        originalRequest.headers.Authorization = `Bearer ${token}`;
                    }
                    return apiClient(originalRequest);
                });
            }

            originalRequest._retry = true;
            isRefreshing = true;

            try {
                const {data} = await axios.post(
                    `${API_URL}/auth/refresh-token`,
                    {},
                    {
                        withCredentials: true,
                        headers: {
                            'Time-Zone': getTimeZone(),
                        },
                    }
                );
                accessToken = data.accessToken;
                processQueue(null, accessToken);

                if (originalRequest.headers) {
                    originalRequest.headers.Authorization = `Bearer ${accessToken}`;
                }
                return apiClient(originalRequest);
            } catch (refreshError) {
                processQueue(refreshError, null);
                accessToken = null;
                if (onAuthFailure) {
                    onAuthFailure();
                }
                return Promise.reject(refreshError);
            } finally {
                isRefreshing = false;
            }
        }

        return Promise.reject(error);
    }
);

export default apiClient;
