import {NavLink, Outlet, useNavigate} from 'react-router-dom';
import {useAuth} from '@/hooks/useAuth';
import {cn} from '@/lib/utils';
import {useState} from 'react';
import {ConfirmDialog} from '@/components/shared/ConfirmDialog';
import {useTranslation} from 'react-i18next';

const navItems = [
    {to: '/decks', label: 'nav:decks', icon: '📚'},
    {to: '/reviews', label: 'nav:reviews', icon: '🔁'},
    {to: '/stats', label: 'nav:statistics', icon: '📊'},
    {to: '/settings', label: 'nav:settings', icon: '⚙️'},
];

export function AppLayout() {
    const {t, i18n} = useTranslation();
    const {username, logout} = useAuth();
    const navigate = useNavigate();
    const [sidebarOpen, setSidebarOpen] = useState(false);
    const [logoutConfirmOpen, setLogoutConfirmOpen] = useState(false);

    const handleLogoutClick = () => setLogoutConfirmOpen(true);

    const handleConfirmLogout = async () => {
        await logout();
        navigate('/login');
    };

    return (
        <div className="min-h-screen bg-slate-50 flex">
            {/* Mobile overlay */}
            {sidebarOpen && (
                <div
                    className="fixed inset-0 bg-black/40 z-30 lg:hidden"
                    onClick={() => setSidebarOpen(false)}
                />
            )}

            {/* Sidebar */}
            <aside
                className={cn(
                    'fixed inset-y-0 left-0 z-40 w-64 bg-white border-r border-slate-200 flex flex-col transition-transform lg:translate-x-0 lg:static lg:z-auto',
                    sidebarOpen ? 'translate-x-0' : '-translate-x-full'
                )}
            >
                <div className="flex items-center gap-2 p-6 border-b border-slate-100">
                    <span className="text-2xl" aria-hidden="true">🧠</span>
                    <h1 className="text-xl font-bold text-slate-900">{t('nav:title')}</h1>
                </div>

                <nav className="flex-1 p-4 space-y-1" role="navigation" aria-label={t('nav:mainNavigation')}>
                    {navItems.map((item) => (
                        <NavLink
                            key={item.to}
                            to={item.to}
                            onClick={() => setSidebarOpen(false)}
                            className={({isActive}) =>
                                cn(
                                    'flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors',
                                    isActive
                                        ? 'bg-indigo-50 text-indigo-700'
                                        : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'
                                )
                            }
                        >
                            <span className="text-lg" aria-hidden="true">{item.icon}</span>
                            {t(item.label)}
                        </NavLink>
                    ))}
                </nav>

                {/* Language switcher */}
                <div className="px-4 py-3 border-t border-slate-100">
                    <div className="flex items-center justify-center gap-1">
                        <button
                            onClick={() => i18n.changeLanguage('en')}
                            className={cn(
                                'px-3 py-1.5 text-xs font-medium rounded-md transition-colors',
                                i18n.language === 'en'
                                    ? 'bg-indigo-100 text-indigo-700'
                                    : 'text-slate-500 hover:text-slate-700 hover:bg-slate-50'
                            )}
                        >
                            {t('common:languageEn')}
                        </button>
                        <span className="text-slate-300 select-none">|</span>
                        <button
                            onClick={() => i18n.changeLanguage('es')}
                            className={cn(
                                'px-3 py-1.5 text-xs font-medium rounded-md transition-colors',
                                i18n.language === 'es'
                                    ? 'bg-indigo-100 text-indigo-700'
                                    : 'text-slate-500 hover:text-slate-700 hover:bg-slate-50'
                            )}
                        >
                            {t('common:languageEs')}
                        </button>
                    </div>
                </div>

                <div className="p-4 border-t border-slate-100">
                    <div className="flex items-center gap-3 mb-3 px-3">
                        <div
                            className="w-8 h-8 rounded-full bg-indigo-100 flex items-center justify-center text-sm font-semibold text-indigo-700">
                            {username?.[0]?.toUpperCase() || '?'}
                        </div>
                        <span className="text-sm font-medium text-slate-700 truncate">{username}</span>
                    </div>
                    <button
                        onClick={handleLogoutClick}
                        className="flex items-center gap-3 w-full rounded-lg px-3 py-2 text-sm font-medium text-slate-500 hover:bg-slate-50 hover:text-slate-700 transition-colors"
                    >
                        <span aria-hidden="true">🚪</span>
                        {t('nav:logOut')}
                    </button>
                </div>
            </aside>

            <ConfirmDialog
                isOpen={logoutConfirmOpen}
                onClose={() => setLogoutConfirmOpen(false)}
                onConfirm={handleConfirmLogout}
                title={t('nav:logOut')}
                message={t('common:areYouSure')}
                confirmLabel={t('nav:logOut')}
            />

            {/* Main content */}
            <div className="flex-1 flex flex-col min-w-0">
                {/* Mobile header */}
                <header
                    className="lg:hidden sticky top-0 z-20 bg-white border-b border-slate-200 px-4 py-3 flex items-center gap-3">
                    <button
                        onClick={() => setSidebarOpen(true)}
                        className="p-1.5 rounded-lg hover:bg-slate-100 transition-colors"
                        aria-label={t('nav:openMenu')}
                    >
                        <svg className="w-6 h-6 text-slate-600" fill="none" viewBox="0 0 24 24" stroke="currentColor"
                             strokeWidth={2}>
                            <path strokeLinecap="round" strokeLinejoin="round" d="M4 6h16M4 12h16M4 18h16"/>
                        </svg>
                    </button>
                    <span className="text-lg font-bold text-slate-900">🧠 {t('nav:decks')}</span>
                </header>

                <main className="flex-1 p-4 lg:p-8 max-w-6xl w-full mx-auto">
                    <Outlet/>
                </main>
            </div>
        </div>
    );
}
