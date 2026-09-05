import {useEffect, useMemo, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {deleteMe, getMe, updateMe} from '@/api/users.api';
import {useAuth} from '@/hooks/useAuth';
import {useTimeZone} from '@/hooks/useTimeZone';
import {Input} from '@/components/ui/Input';
import {Select} from '@/components/ui/Select';
import {Button} from '@/components/ui/Button';
import {Card} from '@/components/ui/Card';
import {SkeletonCard} from '@/components/ui/Skeleton';
import {ErrorState} from '@/components/shared/ErrorState';
import {ConfirmDialog} from '@/components/shared/ConfirmDialog';
import {
    type ChangePasswordFormData,
    changePasswordSchema,
    type ProfileFormData,
    profileSchema,
    translateFieldErrors,
} from '@/lib/validators';
import {sanitizeHtml} from '@/lib/sanitize';
import {toast} from 'sonner';
import {useTranslation} from 'react-i18next';
import i18n from '@/i18n/config';

export function SettingsPage() {
    const {t} = useTranslation();
    const {clearAuth} = useAuth();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const browserTimeZone = useTimeZone();

    const {data: user, isLoading, isError, refetch} = useQuery({

        queryKey: ['users', 'me'],
        queryFn: getMe,
    });

    const timeZoneChanged = useMemo(
        () => user && browserTimeZone !== user.zone,
        [user, browserTimeZone]
    );

    // Profile form
    const [profileForm, setProfileForm] = useState<ProfileFormData>({
        username: '',
        email: '',
        sessionThreshold: 30,
        startOfDay: 6,
        notificationsEnabled: true,
    });
    const [profileErrors, setProfileErrors] = useState<Partial<Record<string, string>>>({});

    // Password form
    const [passwordForm, setPasswordForm] = useState<ChangePasswordFormData>({
        currentPassword: '',
        newPassword: '',
        confirmPassword: '',
    });
    const [passwordErrors, setPasswordErrors] = useState<Partial<Record<string, string>>>({});

    const [deleteConfirm, setDeleteConfirm] = useState(false);

    useEffect(() => {
        if (user) {
            setProfileForm({
                username: user.username,
                email: user.email,
                sessionThreshold: user.sessionThreshold,
                startOfDay: user.startOfDay,
                notificationsEnabled: user.notificationsEnabled,
            });
        }
    }, [user]);

    const updateProfileMutation = useMutation({
        mutationFn: (data: ProfileFormData) =>
            updateMe({
                username: sanitizeHtml(data.username),
                email: data.email,
                sessionThreshold: data.sessionThreshold,
                startOfDay: data.startOfDay,
                notificationsEnabled: data.notificationsEnabled,
            }),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: ['users', 'me']});
            toast.success(t('toast:profileUpdated'));
        },
    });

    const updatePasswordMutation = useMutation({
        mutationFn: (data: ChangePasswordFormData) =>
            updateMe({
                currentPassword: data.currentPassword,
                password: data.newPassword,
            }),
        onSuccess: () => {
            setPasswordForm({currentPassword: '', newPassword: '', confirmPassword: ''});
            toast.success(t('toast:passwordChanged'));
        },
    });

    const deleteAccountMutation = useMutation({
        mutationFn: deleteMe,
        onSuccess: () => {
            clearAuth();
            navigate('/login', {replace: true});
            toast.success(t('toast:accountDeleted'));
        },
    });

    const handleProfileSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        const result = profileSchema.safeParse(profileForm);
        if (!result.success) {
            setProfileErrors(translateFieldErrors(t, result.error));
            return;
        }
        setProfileErrors({});
        updateProfileMutation.mutate(result.data);
    };

    const handlePasswordSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        const result = changePasswordSchema.safeParse(passwordForm);
        if (!result.success) {
            setPasswordErrors(translateFieldErrors(t, result.error));
            return;
        }
        setPasswordErrors({});
        updatePasswordMutation.mutate(result.data);
    };

    if (isLoading) {
        return (
            <div className="max-w-2xl mx-auto space-y-6">
                <SkeletonCard/>
                <SkeletonCard/>
            </div>
        );
    }

    if (isError) return <ErrorState onRetry={() => refetch()}/>;

    return (
        <div className="max-w-2xl mx-auto">
            <div className="mb-8">
                <h1 className="text-2xl font-bold text-slate-900">{t('settings:title')}</h1>
                <p className="text-sm text-slate-500 mt-1">{t('settings:manageProfile')}</p>
            </div>

            {/* Profile Section */}
            <Card className="mb-6">
                <h2 className="text-lg font-semibold text-slate-900 mb-4">{t('settings:profile')}</h2>
                <form onSubmit={handleProfileSubmit} className="space-y-4">
                    <Input
                        label={t('auth:username')}
                        value={profileForm.username}
                        onChange={(e) => {
                            setProfileForm((p) => ({...p, username: e.target.value}));
                            setProfileErrors((p) => ({...p, username: undefined}));
                        }}
                        error={profileErrors.username}
                        disabled={updateProfileMutation.isPending}
                    />
                    <Input
                        label={t('auth:email')}
                        type="email"
                        value={profileForm.email}
                        onChange={(e) => {
                            setProfileForm((p) => ({...p, email: e.target.value}));
                            setProfileErrors((p) => ({...p, email: undefined}));
                        }}
                        error={profileErrors.email}
                        disabled={updateProfileMutation.isPending}
                    />

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label htmlFor="sessionThreshold" className="block text-sm font-medium text-slate-700 mb-1">
                                {t('settings:sessionThreshold')}
                            </label>
                            <input
                                id="sessionThreshold"
                                type="number"
                                min={5}
                                max={360}
                                value={profileForm.sessionThreshold}
                                onChange={(e) =>
                                    setProfileForm((p) => ({...p, sessionThreshold: Number(e.target.value)}))
                                }
                                disabled={updateProfileMutation.isPending}
                                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 disabled:bg-slate-50"
                            />
                        </div>
                        <div>
                            <label htmlFor="startOfDay" className="block text-sm font-medium text-slate-700 mb-1">
                                {t('settings:startOfDay')}
                            </label>
                            <input
                                id="startOfDay"
                                type="number"
                                min={0}
                                max={23}
                                value={profileForm.startOfDay}
                                onChange={(e) =>
                                    setProfileForm((p) => ({...p, startOfDay: Number(e.target.value)}))
                                }
                                disabled={updateProfileMutation.isPending}
                                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 disabled:bg-slate-50"
                            />
                        </div>
                    </div>

                    <div className="flex items-center gap-3">
                        <input
                            id="notifications"
                            type="checkbox"
                            checked={profileForm.notificationsEnabled}
                            onChange={(e) =>
                                setProfileForm((p) => ({...p, notificationsEnabled: e.target.checked}))
                            }
                            disabled={updateProfileMutation.isPending}
                            className="h-4 w-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500"
                        />
                        <label htmlFor="notifications" className="text-sm text-slate-700">
                            {t('settings:enableNotifications')}
                        </label>
                    </div>

                    <Button type="submit" isLoading={updateProfileMutation.isPending}>
                        {t('settings:saveProfile')}
                    </Button>
                </form>
            </Card>

            {/* Timezone Section */}
            {user && (
                <Card className="mb-6">
                    <h2 className="text-lg font-semibold text-slate-900 mb-4">{t('settings:timezone')}</h2>
                    <p className="text-sm text-slate-500 mb-2">
                        {t('settings:timezoneStored', {zone: user.zone})}
                    </p>
                    {timeZoneChanged && (
                        <div
                            className="rounded-lg bg-amber-50 border border-amber-200 px-3 py-2 text-sm text-amber-700">
                            {t('settings:timezoneChanged', {browser: browserTimeZone})}
                        </div>
                    )}
                    <p className="text-xs text-slate-400 mt-2">
                        {t('settings:timezoneHint')}
                    </p>
                </Card>
            )}

            {/* Password Section */}
            <Card className="mb-6">
                <h2 className="text-lg font-semibold text-slate-900 mb-4">{t('settings:changePassword')}</h2>
                <form onSubmit={handlePasswordSubmit} className="space-y-4">
                    <Input
                        label={t('settings:currentPassword')}
                        type="password"
                        value={passwordForm.currentPassword}
                        onChange={(e) => {
                            setPasswordForm((p) => ({...p, currentPassword: e.target.value}));
                            setPasswordErrors((p) => ({...p, currentPassword: undefined}));
                        }}
                        error={passwordErrors.currentPassword}
                        disabled={updatePasswordMutation.isPending}
                        autoComplete="current-password"
                    />
                    <Input
                        label={t('auth:newPassword')}
                        type="password"
                        value={passwordForm.newPassword}
                        onChange={(e) => {
                            setPasswordForm((p) => ({...p, newPassword: e.target.value}));
                            setPasswordErrors((p) => ({...p, newPassword: undefined}));
                        }}
                        error={passwordErrors.newPassword}
                        disabled={updatePasswordMutation.isPending}
                        autoComplete="new-password"
                    />
                    <Input
                        label={t('auth:confirmNewPassword')}
                        type="password"
                        value={passwordForm.confirmPassword}
                        onChange={(e) => {
                            setPasswordForm((p) => ({...p, confirmPassword: e.target.value}));
                            setPasswordErrors((p) => ({...p, confirmPassword: undefined}));
                        }}
                        error={passwordErrors.confirmPassword}
                        disabled={updatePasswordMutation.isPending}
                        autoComplete="new-password"
                    />
                    <Button type="submit" isLoading={updatePasswordMutation.isPending}>
                        {t('settings:changePassword')}
                    </Button>
                </form>
            </Card>

            {/* Language */}
            <Card className="mb-6">
                <h2 className="text-lg font-semibold text-slate-900 mb-4">{t('settings:language')}</h2>
                <p className="text-sm text-slate-500 mb-4">{t('settings:languageDescription')}</p>
                <Select
                    label=""
                    value={i18n.language}
                    onChange={(e) => i18n.changeLanguage(e.target.value)}
                    options={[
                        {value: 'en', label: t('common:languageEn')},
                        {value: 'es', label: t('common:languageEs')},
                    ]}
                />
            </Card>

            {/* Danger Zone */}
            <Card className="border-red-200">
                <h2 className="text-lg font-semibold text-red-600 mb-2">{t('settings:dangerZone')}</h2>
                <p className="text-sm text-slate-500 mb-4">
                    {t('settings:dangerZoneDesc')}
                </p>
                <Button variant="danger" onClick={() => setDeleteConfirm(true)}>
                    {t('settings:deleteAccount')}
                </Button>
            </Card>

            <ConfirmDialog
                isOpen={deleteConfirm}
                onClose={() => setDeleteConfirm(false)}
                onConfirm={() => deleteAccountMutation.mutate()}
                title={t('settings:deleteAccount')}
                message={t('settings:deleteAccountConfirm')}
                confirmLabel={t('settings:deleteMyAccount')}
                isLoading={deleteAccountMutation.isPending}
            />
        </div>
    );
}
