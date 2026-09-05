export interface LoginRequest {
    username: string;
    password: string;
}

export interface RegisterRequest {
    username: string;
    email: string;
    password: string;
    zoneInfo: string;
}

export interface AuthResponse {
    accessToken: string;
    username: string;
}

export interface SignupResponse {
    message: string;
}

export interface ForgotPasswordRequest {
    email: string;
}

export interface ForgotPasswordResponse {
    message: string;
}

export interface ResetPasswordRequest {
    token: string;
    newPassword: string;
}

export interface ResetPasswordResponse {
    message: string;
}
