-- ========== USER ==========
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_username_lower ON users (LOWER (username));
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email_lower ON users (LOWER (email));

-- ========== DECK ==========
CREATE UNIQUE INDEX uk_decks_user_id_name_lower ON decks (user_id, LOWER(name));

-- ========== CARD ==========
CREATE UNIQUE INDEX uk_cards_deck_id_front_lower ON cards (deck_id, LOWER(front));
