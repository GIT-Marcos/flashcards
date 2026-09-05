export interface UserResponse {
    id: number;
    username: string;
    email: string;
    zone: string;
    createdAt: string;
    lastLogin: string | null;
    lastNotificationSent: string | null;
    sessionThreshold: number;
    startOfDay: number;
    notificationsEnabled: boolean;
    roles: string[];
}

export interface UpdateUserRequest {
    username?: string;
    email?: string;
    sessionThreshold?: number;
    startOfDay?: number;
    notificationsEnabled?: boolean;
    currentPassword?: string;
    password?: string;
}
