-- =============================================================================
-- Flyway es la fuente de verdad del esquema de base de datos.
-- Las entidades JPA usan ddl-auto: validate (solo validan, no generan esquema).
-- Los índices funcionales y expresiones SQL se definen AQUÍ, no en @Index de entidades.
-- =============================================================================

-- 1. Tabla de Usuarios
CREATE TABLE users
(
    id                     BIGSERIAL PRIMARY KEY,
    username               VARCHAR(50)  NOT NULL,
    email                  VARCHAR(100) NOT NULL,
    password_hash          VARCHAR(255) NOT NULL,
    zone_info              VARCHAR(50)  NOT NULL,
    last_login             TIMESTAMPTZ,
    last_notification_sent TIMESTAMPTZ,
    session_threshold      INTEGER      NOT NULL DEFAULT 30,
    start_of_day           INTEGER      NOT NULL DEFAULT 6,
    notifications_enabled  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_users_notifications_enabled
    ON users (id) WHERE notifications_enabled = TRUE;

-- 2. Tabla de roles de usuario
CREATE TABLE user_roles
(
    user_id BIGINT      NOT NULL,
    roles   VARCHAR(50) NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, roles),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_user_roles_user_id ON user_roles (user_id);

-- 3. Tabla de Decks
CREATE TABLE decks
(
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(100) NOT NULL,
    has_pending_cards BOOLEAN      NOT NULL DEFAULT FALSE,
    version           INTEGER      NOT NULL DEFAULT 0,
    user_id           BIGINT       NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_decks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_decks_user_id ON decks (user_id);

-- 4. Tabla de Cards
CREATE TABLE cards
(
    id               BIGSERIAL PRIMARY KEY,
    front            VARCHAR(255)     NOT NULL,
    back             TEXT             NOT NULL,
    next_review_date TIMESTAMPTZ      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    interval_days    INTEGER          NOT NULL DEFAULT 0,
    repetition_count INTEGER          NOT NULL DEFAULT 0,
    easiness_factor  DOUBLE PRECISION NOT NULL DEFAULT 2.5,
    deck_id          BIGINT           NOT NULL,
    version          INTEGER          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cards_deck FOREIGN KEY (deck_id) REFERENCES decks (id) ON DELETE CASCADE
);
CREATE INDEX idx_cards_deck_id ON cards (deck_id);

-- 5. Tabla de Sesiones de Estudio
CREATE TABLE study_sessions
(
    id             BIGSERIAL PRIMARY KEY,
    start_time     TIMESTAMPTZ      NOT NULL,
    end_time       TIMESTAMPTZ      NOT NULL,
    cards_reviewed INTEGER          NOT NULL DEFAULT 0,
    accuracy_rate  DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    user_id        BIGINT           NOT NULL,
    version        INTEGER          NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_study_sessions_user_id ON study_sessions (user_id);

-- 6. Tabla de Logs de Revisión
CREATE TABLE card_review_log
(
    id               BIGSERIAL PRIMARY KEY,
    quality          INTEGER          NOT NULL DEFAULT 0,
    easiness_factor  DOUBLE PRECISION NOT NULL DEFAULT 2.5,
    interval_days    INTEGER          NOT NULL DEFAULT 0,
    repetition_count INTEGER          NOT NULL DEFAULT 0,
    next_review_date TIMESTAMPTZ      NOT NULL,
    card_id          BIGINT,
    user_id          BIGINT           NOT NULL,
    session_id       BIGINT           NOT NULL,
    created_at       TIMESTAMPTZ      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_log_card FOREIGN KEY (card_id) REFERENCES cards (id) ON DELETE SET NULL,
    CONSTRAINT fk_log_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_log_session FOREIGN KEY (session_id) REFERENCES study_sessions (id) ON DELETE CASCADE
);
CREATE INDEX idx_card_review_log_card_id ON card_review_log (card_id);
CREATE INDEX idx_card_review_log_user_id ON card_review_log (user_id);
CREATE INDEX idx_card_review_log_session_id ON card_review_log (session_id);
