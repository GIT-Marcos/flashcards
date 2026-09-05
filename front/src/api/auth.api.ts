import apiClient from './client';
import type {
    AuthResponse,
    ForgotPasswordRequest,
    ForgotPasswordResponse,
    LoginRequest,
    RegisterRequest,
    ResetPasswordRequest,
    ResetPasswordResponse,
    SignupResponse
} from '@/types/auth.types';

export async function login(data: LoginRequest): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>('/auth/login', data);
    return response.data;
}

export async function register(data: RegisterRequest): Promise<SignupResponse> {
    const response = await apiClient.post<SignupResponse>('/auth/signup', data);
    return response.data;
}

export async function refreshToken(): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>('/auth/refresh-token');
    return response.data;
}

export async function forgotPassword(data: ForgotPasswordRequest): Promise<ForgotPasswordResponse> {
    const response = await apiClient.post<ForgotPasswordResponse>('/auth/forgot-password', data);
    return response.data;
}

export async function resetPassword(data: ResetPasswordRequest): Promise<ResetPasswordResponse> {
    const response = await apiClient.post<ResetPasswordResponse>('/auth/reset-password', data);
    return response.data;
}

export async function logout(): Promise<void> {
    await apiClient.post('/auth/logout');
}
