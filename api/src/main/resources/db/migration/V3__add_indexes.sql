-- ========== USER ==========
-- 1. Covering index for notification query (UserRepository.findUsersToNotify).
--    zone_info:               equality filter  (WHERE u.zoneInfo = :zoneInfo)
--    last_notification_sent:  range filter     (AND u.lastNotificationSent < :threshold)
--    INCLUDE (id, username, email): permite index-only scans para las columnas del SELECT,
--    evitando visitas a la tabla (heap) y mejorando performance en el barrido de notificaciones.
CREATE INDEX IF NOT EXISTS idx_users_notification_lookup
    ON users (zone_info, last_notification_sent)
    INCLUDE (id, username, email);

-- ========== CARD ==========
-- 2. For pending cards
CREATE INDEX IF NOT EXISTS idx_cards_deck_next_review ON cards (deck_id, next_review_date);
