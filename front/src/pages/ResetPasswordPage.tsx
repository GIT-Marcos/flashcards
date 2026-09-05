import {useEffect, useState} from 'react';
import {Link, useSearchParams} from 'react-router-dom';
import {resetPassword} from '@/api/auth.api';
import {useRateLimit} from '@/hooks/useRateLimit';
import {Input} from '@/components/ui/Input';
import {Button} from '@/components/ui/Button';
import {resetPasswordSchema, translateFieldErrors} from '@/lib/validators';
import {useTranslation} from 'react-i18next';
import {getLocalizedErrorMessage} from '@/lib/errors';

export function ResetPasswordPage() {
    const {t} = useTranslation();
    const [searchParams] = useSearchParams();
    const token = searchParams.get('token') || '';
    const {retryAfter, isRateLimited, handleRateLimitError} = useRateLimit();

    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [errors, setErrors] = useState<Partial<Record<string, string>>>({});
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [serverError, setServerError] = useState('');
    const [successMessage, setSuccessMessage] = useState('');

    useEffect(() => {
        if (token) {
            window.history.replaceState({}, '', '/reset-password');
        }
    }, [token]);

    const handleChange = (field: string, value: string) => {
        if (field === 'newPassword') setNewPassword(value);
        if (field === 'confirmPassword') setConfirmPassword(value);
        setErrors((prev) => ({...prev, [field]: undefined}));
        setServerError('');
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setServerError('');

        const data = {newPassword, confirmPassword};
        const result = resetPasswordSchema.safeParse(data);

        if (!result.success) {
            setErrors(translateFieldErrors(t, result.error));
            return;
        }

        setIsSubmitting(true);
        try {
            const response = await resetPassword({token, newPassword: result.data.newPassword});
            setSuccessMessage(response.message);
        } catch (error) {
            handleRateLimitError(error);
            setServerError(getLocalizedErrorMessage(error, t));
        } finally {
            setIsSubmitting(false);
        }
    };

    if (!token) {
        return (
            <div className="min-h-screen flex items-center justify-center bg-slate-50 p-4">
                <div className="w-full max-w-md">
                    <div className="text-center mb-8">
                        <span className="text-5xl block mb-4">🔒</span>
                        <h1 className="text-3xl font-bold text-slate-900">{t('auth:invalidResetLink')}</h1>
                        <p className="text-slate-500 mt-2">{t('auth:invalidResetLinkDesc')}</p>
                    </div>

                    <div className="bg-white rounded-2xl shadow-sm border border-slate-200 p-8 text-center space-y-4">
                        <p className="text-sm text-slate-600">
                            {t('auth:invalidResetLinkMsg')}
                        </p>
                        <Link
                            to="/forgot-password"
                            className="inline-flex justify-center items-center w-full bg-indigo-600 text-white rounded-xl py-3 px-4 text-sm font-medium hover:bg-indigo-700 transition"
                        >
                            {t('auth:requestNewResetLink')}
                        </Link>
                    </div>
                </div>
            </div>
        );
    }

    return (
        <div className="min-h-screen flex items-center justify-center bg-slate-50 p-4">
            <div className="w-full max-w-md">
                <div className="text-center mb-8">
                    <span className="text-5xl block mb-4">🔒</span>
                    <h1 className="text-3xl font-bold text-slate-900">{t('auth:resetPasswordTitle')}</h1>
                    <p className="text-slate-500 mt-2">{t('auth:resetPasswordDesc')}</p>
                </div>

                <div className="bg-white rounded-2xl shadow-sm border border-slate-200 p-8">
                    {successMessage ? (
                        <div className="text-center space-y-6">
                            <span className="text-5xl block">✅</span>
                            <h2 className="text-2xl font-bold text-slate-900">{t('auth:passwordResetSuccess')}</h2>
                            <p className="text-sm text-slate-600 leading-relaxed">{successMessage}</p>
                            <div className="pt-4 border-t border-slate-100">
                                <Link
                                    to="/login"
                                    className="inline-flex justify-center items-center w-full bg-indigo-600 text-white rounded-xl py-3 px-4 text-sm font-medium hover:bg-indigo-700 transition"
                                >
                                    {t('auth:goToSignIn')}
                                </Link>
                            </div>
                        </div>
                    ) : (
                        <form onSubmit={handleSubmit} className="space-y-4">
                            <Input
                                label={t('auth:newPassword')}
                                type="password"
                                value={newPassword}
                                onChange={(e) => handleChange('newPassword', e.target.value)}
                                error={errors.newPassword}
                                disabled={isSubmitting}
                                autoComplete="new-password"
                                autoFocus
                            />
                            <Input
                                label={t('auth:confirmNewPassword')}
                                type="password"
                                value={confirmPassword}
                                onChange={(e) => handleChange('confirmPassword', e.target.value)}
                                error={errors.confirmPassword}
                                disabled={isSubmitting}
                                autoComplete="new-password"
                            />

                            {serverError && (
                                <p className="text-sm text-red-600 bg-red-50 rounded-lg px-3 py-2" role="alert">
                                    {serverError}
                                </p>
                            )}

                            <Button
                                type="submit"
                                className="w-full"
                                size="lg"
                                isLoading={isSubmitting}
                                disabled={isRateLimited}
                            >
                                {isRateLimited ? t('auth:tryAgainIn', {seconds: retryAfter}) : t('auth:resetPassword')}
                            </Button>
                        </form>
                    )}
                </div>
            </div>
        </div>
    );
}
