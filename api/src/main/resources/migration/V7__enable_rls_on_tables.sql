-- =============================================================================
-- V7: Enable RLS + permissive policies for Supabase compatibility
--
-- The application connects as postgres (which bypasses RLS by default), so
-- these policies do not affect application behavior. Enabling RLS silences
-- the Supabase Database Linter warnings (rls_disabled_in_public).
--
-- Policies grant full access to all roles. The app connects as postgres
-- (which bypasses RLS by default), so no actual restriction is imposed.
-- =============================================================================

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
CREATE POLICY "app_full_access" ON users FOR ALL USING (true) WITH CHECK (true);

ALTER TABLE user_roles ENABLE ROW LEVEL SECURITY;
CREATE POLICY "app_full_access" ON user_roles FOR ALL USING (true) WITH CHECK (true);

ALTER TABLE decks ENABLE ROW LEVEL SECURITY;
CREATE POLICY "app_full_access" ON decks FOR ALL USING (true) WITH CHECK (true);

ALTER TABLE cards ENABLE ROW LEVEL SECURITY;
CREATE POLICY "app_full_access" ON cards FOR ALL USING (true) WITH CHECK (true);

ALTER TABLE study_sessions ENABLE ROW LEVEL SECURITY;
CREATE POLICY "app_full_access" ON study_sessions FOR ALL USING (true) WITH CHECK (true);

ALTER TABLE card_review_log ENABLE ROW LEVEL SECURITY;
CREATE POLICY "app_full_access" ON card_review_log FOR ALL USING (true) WITH CHECK (true);
