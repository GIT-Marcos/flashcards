-- ========== CARD ==========
-- 1. . Pagination. Works for both: findAll, findAllWithPendingCards
CREATE INDEX IF NOT EXISTS idx_cards_deck_pagination_desc ON cards (deck_id, next_review_date DESC, id DESC);
-- 2. Pagination
CREATE INDEX IF NOT EXISTS idx_cards_deck_pagination_asc ON cards (deck_id, next_review_date ASC, id ASC);

-- ========== DECK ==========
-- 3. Pagination
CREATE INDEX IF NOT EXISTS idx_deck_user_pending_created_id_desc ON
    decks (user_id, has_pending_cards, created_at DESC, id DESC);
-- 4. Pagination
CREATE INDEX IF NOT EXISTS idx_deck_user_pending_created_id_asc ON
    decks (user_id, has_pending_cards, created_at ASC, id ASC);

-- ========== SESSION ==========
-- 5. Pagination
CREATE INDEX IF NOT EXISTS idx_study_sessions_user_pagination_asc ON
    study_sessions (user_id, start_time ASC, id ASC);
-- 6. Pagination
CREATE INDEX IF NOT EXISTS idx_study_sessions_user_pagination_desc ON
    study_sessions (user_id, start_time DESC, id DESC);
