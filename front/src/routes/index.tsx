import {BrowserRouter, Navigate, Route, Routes} from 'react-router-dom';
import {ProtectedRoute} from './ProtectedRoute';
import {AppLayout} from '@/components/layout/AppLayout';
import {LoginPage} from '@/pages/LoginPage';
import {RegisterPage} from '@/pages/RegisterPage';
import {ForgotPasswordPage} from '@/pages/ForgotPasswordPage';
import {ResetPasswordPage} from '@/pages/ResetPasswordPage';
import {DeckListPage} from '@/pages/DeckListPage';
import {DeckDetailPage} from '@/pages/DeckDetailPage';
import {DeckFormPage} from '@/pages/DeckFormPage';
import {StudyPage} from '@/pages/StudyPage';
import {StatsPage} from '@/pages/StatsPage';
import {SettingsPage} from '@/pages/SettingsPage';
import {ReviewsPage} from '@/pages/ReviewsPage';
import {NotFoundPage} from '@/pages/NotFoundPage';

export function AppRouter() {
    return (
        <BrowserRouter>
            <Routes>
                {/* Public routes */}
                <Route path="/login" element={<LoginPage/>}/>
                <Route path="/register" element={<RegisterPage/>}/>
                <Route path="/forgot-password" element={<ForgotPasswordPage/>}/>
                <Route path="/auth/reset-password" element={<ResetPasswordPage/>}/>

                {/* Protected routes */}
                <Route element={<ProtectedRoute/>}>
                    <Route element={<AppLayout/>}>
                        <Route path="/" element={<Navigate to="/decks" replace/>}/>
                        <Route path="/decks" element={<DeckListPage/>}/>
                        <Route path="/reviews" element={<ReviewsPage/>}/>
                        <Route path="/decks/new" element={<DeckFormPage/>}/>
                        <Route path="/decks/:deckId" element={<DeckDetailPage/>}/>
                        <Route path="/decks/:deckId/edit" element={<DeckFormPage/>}/>
                        <Route path="/decks/:deckId/study" element={<StudyPage/>}/>
                        <Route path="/stats" element={<StatsPage/>}/>
                        <Route path="/settings" element={<SettingsPage/>}/>
                    </Route>
                </Route>

                {/* 404 */}
                <Route path="*" element={<NotFoundPage/>}/>
            </Routes>
        </BrowserRouter>
    );
}
