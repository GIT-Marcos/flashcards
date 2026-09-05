import {z} from 'zod';

const passwordSchema = z
    .string()
    .min(8, 'passwordMin')
    .max(20, 'passwordMax')
    .refine((val) => /[a-z]/.test(val), 'passwordLowercase')
    .refine((val) => /[A-Z]/.test(val), 'passwordUppercase')
    .refine((val) => /\d/.test(val), 'passwordDigit')
    .refine((val) => /[@#$%^&+=!]/.test(val), 'passwordSpecial')
    .refine((val) => !/\s/.test(val), 'passwordNoWhitespace');

export const loginSchema = z.object({
    username: z.string().min(4, 'usernameMin').max(50, 'usernameMax50'),
    password: z.string().min(1, 'passwordRequired'),
});

export const registerSchema = z
    .object({
        username: z.string().min(4, 'usernameMin').max(50, 'usernameMax50'),
        email: z.string().email('emailInvalid'),
        password: passwordSchema,
        confirmPassword: z.string(),
        zoneInfo: z.string(),
    })
    .refine((data) => data.password === data.confirmPassword, {
        message: 'passwordsNoMatch',
        path: ['confirmPassword'],
    });

export const deckSchema = z.object({
    name: z.string().min(1, 'deckNameRequired').max(100, 'deckNameMax'),
});

export const cardSchema = z.object({
    front: z.string().min(1, 'frontRequired').max(255, 'frontMax'),
    back: z.string().min(1, 'backRequired').max(5000, 'backMax'),
});

export const profileSchema = z.object({
    username: z.string().min(4, 'usernameMin').max(20, 'usernameMax20'),
    email: z.string().email('emailInvalid').max(100, 'emailMax'),
    sessionThreshold: z.number().min(5).max(360),
    startOfDay: z.number().min(0).max(23),
    notificationsEnabled: z.boolean(),
});

export const changePasswordSchema = z
    .object({
        currentPassword: z.string().min(1, 'currentPasswordRequired'),
        newPassword: passwordSchema,
        confirmPassword: z.string(),
    })
    .refine((data) => data.newPassword === data.confirmPassword, {
        message: 'passwordsNoMatch',
        path: ['confirmPassword'],
    });

export type LoginFormData = z.infer<typeof loginSchema>;
export type RegisterFormData = z.infer<typeof registerSchema>;
export type DeckFormData = z.infer<typeof deckSchema>;
export type CardFormData = z.infer<typeof cardSchema>;
export type ProfileFormData = z.infer<typeof profileSchema>;
export type ChangePasswordFormData = z.infer<typeof changePasswordSchema>;

export const forgotPasswordSchema = z.object({
    email: z.string().email('emailInvalid'),
});

export const resetPasswordSchema = z
    .object({
        newPassword: passwordSchema,
        confirmPassword: z.string(),
    })
    .refine((data) => data.newPassword === data.confirmPassword, {
        message: 'passwordsNoMatch',
        path: ['confirmPassword'],
    });

export type ForgotPasswordFormData = z.infer<typeof forgotPasswordSchema>;
export type ResetPasswordFormData = z.infer<typeof resetPasswordSchema>;

export function translateFieldErrors(
    t: (key: string) => string,
    error: z.ZodError
): Record<string, string> {
    const fieldErrors: Record<string, string> = {};
    for (const issue of error.issues) {
        const field = String(issue.path[0]);
        if (!fieldErrors[field]) {
            fieldErrors[field] = t(`validation:${issue.message}`);
        }
    }
    return fieldErrors;
}
