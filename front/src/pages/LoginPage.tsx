import {useState} from 'react';
import {Link, useNavigate} from 'react-router-dom';
import {useAuth} from '@/hooks/useAuth';
import {useRateLimit} from '@/hooks/useRateLimit';
import {Input} from '@/components/ui/Input';
import {Button} from '@/components/ui/Button';
import {LoginFormData, loginSchema, translateFieldErrors} from '@/lib/validators';
import {toast} from 'sonner';
import {useTranslation} from 'react-i18next';
import {getLocalizedErrorMessage} from '@/lib/errors';

export function LoginPage() {
    const {t} = useTranslation();
    const {login} = useAuth();
    const navigate = useNavigate();
    const {retryAfter, isRateLimited, handleRateLimitError} = useRateLimit();

    const [form, setForm] = useState<LoginFormData>({username: '', password: ''});
    const [errors, setErrors] = useState<Partial<Record<keyof LoginFormData, string>>>({});
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [serverError, setServerError] = useState('');

    const handleChange = (field: keyof LoginFormData, value: string) => {
        setForm((prev) => ({...prev, [field]: value}));
        setErrors((prev) => ({...prev, [field]: undefined}));
        setServerError('');
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setServerError('');

        const result = loginSchema.safeParse(form);
        if (!result.success) {
            setErrors(translateFieldErrors(t, result.error));
            return;
        }

        setIsSubmitting(true);
        try {
            await login(result.data);
            toast.success(t('toast:welcomeBack'));
            navigate('/decks', {replace: true});
        } catch (error) {
            handleRateLimitError(error);
            setServerError(getLocalizedErrorMessage(error, t));
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <div className="min-h-screen flex items-center justify-center bg-slate-50 p-4">
            <div className="w-full max-w-md">
                <div className="text-center mb-8">
                    <span className="text-5xl block mb-4">🧠</span>
                    <h1 className="text-3xl font-bold text-slate-900">{t('auth:welcomeBack')}</h1>
                    <p className="text-slate-500 mt-2">{t('auth:signInToContinue')}</p>
                </div>

                <div className="bg-white rounded-2xl shadow-sm border border-slate-200 p-8">
                    <form onSubmit={handleSubmit} className="space-y-4">
                        <Input
                            label={t('auth:username')}
                            value={form.username}
                            onChange={(e) => handleChange('username', e.target.value)}
                            error={errors.username}
                            disabled={isSubmitting}
                            autoComplete="username"
                            autoFocus
                        />
                        <Input
                            label={t('auth:password')}
                            type="password"
                            value={form.password}
                            onChange={(e) => handleChange('password', e.target.value)}
                            error={errors.password}
                            disabled={isSubmitting}
                            autoComplete="current-password"
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
                            {isRateLimited ? t('auth:tryAgainIn', {seconds: retryAfter}) : t('auth:signIn')}
                        </Button>

                        <p className="text-center">
                            <Link to="/forgot-password"
                                  className="text-sm text-indigo-600 hover:text-indigo-700 font-medium">
                                {t('auth:forgotPassword')}
                            </Link>
                        </p>
                    </form>
                </div>

                <p className="text-center text-sm text-slate-500 mt-6">
                    {t('auth:dontHaveAccount')}{' '}
                    <Link to="/register" className="text-indigo-600 hover:text-indigo-700 font-medium">
                        {t('auth:createAccount')}
                    </Link>
                </p>
            </div>
        </div>
    );
}
