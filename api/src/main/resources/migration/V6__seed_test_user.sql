-- =============================================================================
-- Flyway V7: Seed test user with realistic data
--
-- Creates a test_user with 7 decks (~83 cards), 5 completed study sessions,
-- and ~72 review logs with varied quality ratings (SM-2 spaced repetition).
-- Idempotent: skips if test_user already exists.
-- =============================================================================

DO $$
DECLARE
    uid BIGINT;
    d1  BIGINT; d2  BIGINT; d3  BIGINT; d4  BIGINT; d5  BIGINT; d6  BIGINT; d7  BIGINT;
    s1  BIGINT; s2  BIGINT; s3  BIGINT; s4  BIGINT; s5  BIGINT;
    s1_time TIMESTAMPTZ := NOW() - INTERVAL '4 days';
    s2_time TIMESTAMPTZ := NOW() - INTERVAL '3 days';
    s3_time TIMESTAMPTZ := NOW() - INTERVAL '2 days';
    s4_time TIMESTAMPTZ := NOW() - INTERVAL '1 day';
    s5_time TIMESTAMPTZ := NOW() - INTERVAL '2 hours';
BEGIN
    IF EXISTS (SELECT 1 FROM users WHERE username = 'test_user') THEN
        RETURN;
    END IF;

    -- ==============================
    -- 1. USER
    -- ==============================
    INSERT INTO users (username, email, password_hash, zone_info)
    VALUES (
        '${TEST_USERNAME}',
        '${TEST_USER_EMAIL}',
        '${TEST_USER_PSW_HASH}',
        '${TEST_USER_ZONE}'
    )
    RETURNING id INTO uid;

    INSERT INTO user_roles (user_id, roles) VALUES (uid, 'ROLE_USER');

    -- ==============================
    -- 2. DECKS (7)
    -- ==============================
    INSERT INTO decks (name, user_id, has_pending_cards)
    VALUES ('Saludos y Cortesía', uid, TRUE) RETURNING id INTO d1;
    INSERT INTO decks (name, user_id, has_pending_cards)
    VALUES ('Comida y Bebida', uid, TRUE) RETURNING id INTO d2;
    INSERT INTO decks (name, user_id, has_pending_cards)
    VALUES ('Viajes y Direcciones', uid, TRUE) RETURNING id INTO d3;
    INSERT INTO decks (name, user_id, has_pending_cards)
    VALUES ('Números y Tiempo', uid, TRUE) RETURNING id INTO d4;
    INSERT INTO decks (name, user_id, has_pending_cards)
    VALUES ('Familia y Personas', uid, TRUE) RETURNING id INTO d5;
    INSERT INTO decks (name, user_id, has_pending_cards)
    VALUES ('Trabajo y Oficina', uid, TRUE) RETURNING id INTO d6;
    INSERT INTO decks (name, user_id, has_pending_cards)
    VALUES ('Verbos Comunes', uid, TRUE) RETURNING id INTO d7;

    -- ==============================
    -- 3. CARDS (83 total)
    -- ==============================

    -- Deck 1: Saludos y Cortesía (12 cards)
    INSERT INTO cards (front, back, next_review_date, interval_days, repetition_count, easiness_factor, deck_id)
    VALUES
        ('Hola',              'Hello',              NOW(),                                   0, 0, 2.5,  d1),
        ('Buenos días',       'Good morning',       s1_time + INTERVAL '1 day',              1, 1, 2.5,  d1),
        ('Buenas tardes',     'Good afternoon',     s1_time + INTERVAL '1 day',              1, 0, 1.96, d1),
        ('Buenas noches',     'Good night',         s1_time + INTERVAL '1 day',              1, 1, 2.36, d1),
        ('¿Cómo estás?',      'How are you?',       s1_time + INTERVAL '1 day',              1, 1, 2.5,  d1),
        ('Mucho gusto',       'Nice to meet you',   s1_time + INTERVAL '6 days',             6, 2, 2.6,  d1),
        ('Por favor',         'Please',             s1_time + INTERVAL '6 days',             6, 2, 2.6,  d1),
        ('Gracias',           'Thank you',          s1_time + INTERVAL '15 days',           15, 3, 2.5,  d1),
        ('De nada',           'You''re welcome',    s1_time + INTERVAL '15 days',           15, 3, 2.36, d1),
        ('Perdón',            'Excuse me',          s1_time + INTERVAL '20 days',           20, 4, 2.6,  d1),
        ('Hasta luego',       'See you later',      s1_time + INTERVAL '20 days',           20, 4, 2.5,  d1),
        ('Salud',             'Cheers',             s1_time + INTERVAL '30 days',           30, 5, 2.6,  d1);

    -- Deck 2: Comida y Bebida (13 cards)
    INSERT INTO cards (front, back, next_review_date, interval_days, repetition_count, easiness_factor, deck_id)
    VALUES
        ('Agua',              'Water',              NOW(),                                   0, 0, 2.5,  d2),
        ('Pan',               'Bread',              NOW(),                                   0, 0, 2.5,  d2),
        ('Leche',             'Milk',               s1_time + INTERVAL '1 day',              1, 0, 2.18, d2),
        ('Café',              'Coffee',             s1_time + INTERVAL '1 day',              1, 1, 2.36, d2),
        ('Cerveza',           'Beer',               s1_time + INTERVAL '1 day',              1, 0, 2.18, d2),
        ('Vino',              'Wine',               s1_time + INTERVAL '1 day',              1, 0, 1.96, d2),
        ('Fruta',             'Fruit',              s2_time + INTERVAL '6 days',             6, 2, 2.5,  d2),
        ('Verdura',           'Vegetable',          s2_time + INTERVAL '6 days',             6, 2, 2.36, d2),
        ('Carne',             'Meat',               s2_time + INTERVAL '15 days',           15, 3, 2.5,  d2),
        ('Pollo',             'Chicken',            s2_time + INTERVAL '15 days',           15, 3, 2.6,  d2),
        ('Pescado',           'Fish',               s2_time + INTERVAL '20 days',           20, 4, 2.6,  d2),
        ('Arroz',             'Rice',               s2_time + INTERVAL '25 days',           25, 4, 2.36, d2),
        ('Huevo',             'Egg',                s2_time + INTERVAL '30 days',           30, 5, 2.6,  d2);

    -- Deck 3: Viajes y Direcciones (12 cards)
    INSERT INTO cards (front, back, next_review_date, interval_days, repetition_count, easiness_factor, deck_id)
    VALUES
        ('Aeropuerto',        'Airport',            NOW(),                                   0, 0, 2.5,  d3),
        ('Estación',          'Station',            s2_time + INTERVAL '1 day',              1, 0, 2.18, d3),
        ('Hotel',             'Hotel',              s2_time + INTERVAL '1 day',              1, 1, 2.36, d3),
        ('Restaurante',       'Restaurant',         s2_time + INTERVAL '1 day',              1, 1, 2.5,  d3),
        ('Museo',             'Museum',             s2_time + INTERVAL '1 day',              1, 1, 2.36, d3),
        ('Playa',             'Beach',              s2_time + INTERVAL '6 days',             6, 2, 2.6,  d3),
        ('Calle',             'Street',             s2_time + INTERVAL '6 days',             6, 2, 2.6,  d3),
        ('Mapa',              'Map',                s4_time + INTERVAL '15 days',           15, 3, 2.5,  d3),
        ('Pasaporte',         'Passport',           s4_time + INTERVAL '15 days',           15, 3, 2.6,  d3),
        ('Equipaje',          'Luggage',            s5_time + INTERVAL '20 days',           20, 4, 2.6,  d3),
        ('Billete',           'Ticket',             s2_time + INTERVAL '1 day',              1, 0, 1.96, d3),
        ('Reserva',           'Reservation',        s2_time + INTERVAL '1 day',              1, 0, 1.96, d3);

    -- Deck 4: Números y Tiempo (10 cards)
    INSERT INTO cards (front, back, next_review_date, interval_days, repetition_count, easiness_factor, deck_id)
    VALUES
        ('Uno',               'One',                NOW(),                                   0, 0, 2.5,  d4),
        ('Dos',               'Two',                NOW(),                                   0, 0, 2.5,  d4),
        ('Tres',              'Three',              s3_time + INTERVAL '1 day',              1, 0, 2.18, d4),
        ('Diez',              'Ten',                s3_time + INTERVAL '1 day',              1, 1, 2.36, d4),
        ('Cien',              'One hundred',        s3_time + INTERVAL '1 day',              1, 1, 2.5,  d4),
        ('Lunes',             'Monday',             s3_time + INTERVAL '6 days',             6, 2, 2.5,  d4),
        ('Enero',             'January',            s3_time + INTERVAL '6 days',             6, 2, 2.6,  d4),
        ('Hoy',               'Today',              s3_time + INTERVAL '15 days',           15, 3, 2.5,  d4),
        ('Mañana',            'Tomorrow',           s3_time + INTERVAL '20 days',           20, 4, 2.6,  d4),
        ('Ayer',              'Yesterday',          s3_time + INTERVAL '25 days',           25, 4, 2.5,  d4);

    -- Deck 5: Familia y Personas (11 cards)
    INSERT INTO cards (front, back, next_review_date, interval_days, repetition_count, easiness_factor, deck_id)
    VALUES
        ('Madre',             'Mother',             NOW(),                                   0, 0, 2.5,  d5),
        ('Padre',             'Father',             s3_time + INTERVAL '1 day',              1, 0, 1.96, d5),
        ('Hermano',           'Brother',            s3_time + INTERVAL '1 day',              1, 1, 2.36, d5),
        ('Hermana',           'Sister',             s3_time + INTERVAL '1 day',              1, 0, 2.18, d5),
        ('Hijo',              'Son',                s3_time + INTERVAL '1 day',              1, 0, 2.18, d5),
        ('Hija',              'Daughter',           s3_time + INTERVAL '6 days',             6, 2, 2.36, d5),
        ('Amigo',             'Friend (male)',      s3_time + INTERVAL '1 day',              1, 0, 1.96, d5),
        ('Amiga',             'Friend (female)',    s3_time + INTERVAL '1 day',              1, 0, 2.18, d5),
        ('Niño',              'Boy',                s4_time + INTERVAL '15 days',           15, 3, 2.5,  d5),
        ('Niña',              'Girl',               s4_time + INTERVAL '15 days',           15, 3, 2.36, d5),
        ('Persona',           'Person',             s4_time + INTERVAL '25 days',           25, 4, 2.6,  d5);

    -- Deck 6: Trabajo y Oficina (10 cards)
    INSERT INTO cards (front, back, next_review_date, interval_days, repetition_count, easiness_factor, deck_id)
    VALUES
        ('Trabajo',           'Work',               NOW(),                                   0, 0, 2.5,  d6),
        ('Oficina',           'Office',             s4_time + INTERVAL '1 day',              1, 0, 2.18, d6),
        ('Jefe',              'Boss',               s4_time + INTERVAL '1 day',              1, 1, 2.36, d6),
        ('Reunión',           'Meeting',            s4_time + INTERVAL '1 day',              1, 1, 2.5,  d6),
        ('Teléfono',          'Phone',              s4_time + INTERVAL '6 days',             6, 2, 2.5,  d6),
        ('Correo',            'Email',              s4_time + INTERVAL '6 days',             6, 2, 2.6,  d6),
        ('Escritorio',        'Desk',               s4_time + INTERVAL '15 days',           15, 3, 2.5,  d6),
        ('Ordenador',         'Computer',           s4_time + INTERVAL '15 days',           15, 3, 2.5,  d6),
        ('Documento',         'Document',           s4_time + INTERVAL '20 days',           20, 4, 2.6,  d6),
        ('Salario',           'Salary',             s4_time + INTERVAL '30 days',           30, 5, 2.6,  d6);

    -- Deck 7: Verbos Comunes (15 cards)
    INSERT INTO cards (front, back, next_review_date, interval_days, repetition_count, easiness_factor, deck_id)
    VALUES
        ('Ser',               'To be (permanent)',  NOW(),                                   0, 0, 2.5,  d7),
        ('Estar',             'To be (temporary)',  NOW(),                                   0, 0, 2.5,  d7),
        ('Tener',             'To have',            s5_time + INTERVAL '1 day',              1, 0, 2.18, d7),
        ('Hacer',             'To do / To make',    s5_time + INTERVAL '1 day',              1, 0, 2.18, d7),
        ('Decir',             'To say',             s5_time + INTERVAL '1 day',              1, 1, 2.5,  d7),
        ('Ir',                'To go',              s5_time + INTERVAL '1 day',              1, 1, 2.6,  d7),
        ('Ver',               'To see',             s5_time + INTERVAL '6 days',             6, 2, 2.36, d7),
        ('Saber',             'To know',            s5_time + INTERVAL '6 days',             6, 2, 2.5,  d7),
        ('Poder',             'To be able to',      s5_time + INTERVAL '15 days',           15, 3, 2.5,  d7),
        ('Querer',            'To want',            s5_time + INTERVAL '15 days',           15, 3, 2.36, d7),
        ('Gustar',            'To like',            s5_time + INTERVAL '20 days',           20, 4, 2.6,  d7),
        ('Comer',             'To eat',             s5_time + INTERVAL '20 days',           20, 4, 2.5,  d7),
        ('Beber',             'To drink',           s5_time + INTERVAL '25 days',           25, 4, 2.36, d7),
        ('Dormir',           'To sleep',           s5_time + INTERVAL '30 days',           30, 5, 2.6,  d7),
        ('Leer',              'To read',            NOW(),                                   0, 0, 2.5,  d7);

    -- ==============================
    -- 4. STUDY SESSIONS (5)
    -- ==============================
    INSERT INTO study_sessions (start_time, end_time, cards_reviewed, accuracy_rate, user_id)
    VALUES (s1_time, s1_time + INTERVAL '30 minutes', 15, 0.73, uid)
    RETURNING id INTO s1;

    INSERT INTO study_sessions (start_time, end_time, cards_reviewed, accuracy_rate, user_id)
    VALUES (s2_time, s2_time + INTERVAL '25 minutes', 15, 0.80, uid)
    RETURNING id INTO s2;

    INSERT INTO study_sessions (start_time, end_time, cards_reviewed, accuracy_rate, user_id)
    VALUES (s3_time, s3_time + INTERVAL '35 minutes', 15, 0.60, uid)
    RETURNING id INTO s3;

    INSERT INTO study_sessions (start_time, end_time, cards_reviewed, accuracy_rate, user_id)
    VALUES (s4_time, s4_time + INTERVAL '20 minutes', 14, 0.93, uid)
    RETURNING id INTO s4;

    INSERT INTO study_sessions (start_time, end_time, cards_reviewed, accuracy_rate, user_id)
    VALUES (s5_time, s5_time + INTERVAL '45 minutes', 13, 0.77, uid)
    RETURNING id INTO s5;

    -- ==============================
    -- 5. REVIEW LOGS (72 total)
    -- ==============================

    -- Session 1 (4 days ago, 15 reviews) — Deck 1 + Deck 2
    INSERT INTO card_review_log (quality, easiness_factor, interval_days, repetition_count, next_review_date, card_id, user_id, session_id)
    VALUES
        (4, 2.5,  1,  1,  s1_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d1 AND front = 'Buenos días'),     uid, s1),
        (1, 1.96, 1,  0,  s1_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d1 AND front = 'Buenas tardes'),   uid, s1),
        (3, 2.36, 1,  1,  s1_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d1 AND front = 'Buenas noches'),   uid, s1),
        (4, 2.5,  1,  1,  s1_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d1 AND front = '¿Cómo estás?'),    uid, s1),
        (5, 2.6,  6,  2,  s1_time + INTERVAL '6 days', (SELECT id FROM cards WHERE deck_id = d1 AND front = 'Mucho gusto'),     uid, s1),
        (5, 2.6,  6,  2,  s1_time + INTERVAL '6 days', (SELECT id FROM cards WHERE deck_id = d1 AND front = 'Por favor'),       uid, s1),
        (4, 2.5,  15, 3,  s1_time + INTERVAL '15 days',(SELECT id FROM cards WHERE deck_id = d1 AND front = 'Gracias'),         uid, s1),
        (3, 2.36, 15, 3,  s1_time + INTERVAL '15 days',(SELECT id FROM cards WHERE deck_id = d1 AND front = 'De nada'),         uid, s1),
        (5, 2.6,  20, 4,  s1_time + INTERVAL '20 days',(SELECT id FROM cards WHERE deck_id = d1 AND front = 'Perdón'),          uid, s1),
        (4, 2.5,  20, 4,  s1_time + INTERVAL '20 days',(SELECT id FROM cards WHERE deck_id = d1 AND front = 'Hasta luego'),     uid, s1),
        (4, 2.6,  30, 5,  s1_time + INTERVAL '30 days',(SELECT id FROM cards WHERE deck_id = d1 AND front = 'Salud'),           uid, s1),
        (2, 2.18, 1,  0,  s1_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d2 AND front = 'Leche'),           uid, s1),
        (3, 2.36, 1,  1,  s1_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d2 AND front = 'Café'),            uid, s1),
        (2, 2.18, 1,  0,  s1_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d2 AND front = 'Cerveza'),         uid, s1),
        (1, 1.96, 1,  0,  s1_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d2 AND front = 'Vino'),            uid, s1);

    -- Session 2 (3 days ago, 15 reviews) — Deck 2 + Deck 3
    INSERT INTO card_review_log (quality, easiness_factor, interval_days, repetition_count, next_review_date, card_id, user_id, session_id)
    VALUES
        (4, 2.5,  6,  2,  s2_time + INTERVAL '6 days', (SELECT id FROM cards WHERE deck_id = d2 AND front = 'Fruta'),           uid, s2),
        (3, 2.36, 6,  2,  s2_time + INTERVAL '6 days', (SELECT id FROM cards WHERE deck_id = d2 AND front = 'Verdura'),         uid, s2),
        (4, 2.5,  15, 3,  s2_time + INTERVAL '15 days',(SELECT id FROM cards WHERE deck_id = d2 AND front = 'Carne'),           uid, s2),
        (5, 2.6,  15, 3,  s2_time + INTERVAL '15 days',(SELECT id FROM cards WHERE deck_id = d2 AND front = 'Pollo'),           uid, s2),
        (4, 2.6,  20, 4,  s2_time + INTERVAL '20 days',(SELECT id FROM cards WHERE deck_id = d2 AND front = 'Pescado'),         uid, s2),
        (3, 2.36, 25, 4,  s2_time + INTERVAL '25 days',(SELECT id FROM cards WHERE deck_id = d2 AND front = 'Arroz'),           uid, s2),
        (4, 2.6,  30, 5,  s2_time + INTERVAL '30 days',(SELECT id FROM cards WHERE deck_id = d2 AND front = 'Huevo'),           uid, s2),
        (2, 2.18, 1,  0,  s2_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d3 AND front = 'Estación'),        uid, s2),
        (3, 2.36, 1,  1,  s2_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d3 AND front = 'Hotel'),           uid, s2),
        (4, 2.5,  1,  1,  s2_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d3 AND front = 'Restaurante'),     uid, s2),
        (3, 2.36, 1,  1,  s2_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d3 AND front = 'Museo'),           uid, s2),
        (4, 2.6,  6,  2,  s2_time + INTERVAL '6 days', (SELECT id FROM cards WHERE deck_id = d3 AND front = 'Playa'),           uid, s2),
        (5, 2.6,  6,  2,  s2_time + INTERVAL '6 days', (SELECT id FROM cards WHERE deck_id = d3 AND front = 'Calle'),           uid, s2),
        (1, 1.96, 1,  0,  s2_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d3 AND front = 'Billete'),         uid, s2),
        (1, 1.96, 1,  0,  s2_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d3 AND front = 'Reserva'),         uid, s2);

    -- Session 3 (2 days ago, 15 reviews) — Deck 4 + Deck 5
    INSERT INTO card_review_log (quality, easiness_factor, interval_days, repetition_count, next_review_date, card_id, user_id, session_id)
    VALUES
        (2, 2.18, 1,  0,  s3_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d4 AND front = 'Tres'),            uid, s3),
        (3, 2.36, 1,  1,  s3_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d4 AND front = 'Diez'),            uid, s3),
        (4, 2.5,  1,  1,  s3_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d4 AND front = 'Cien'),            uid, s3),
        (4, 2.5,  6,  2,  s3_time + INTERVAL '6 days', (SELECT id FROM cards WHERE deck_id = d4 AND front = 'Lunes'),           uid, s3),
        (4, 2.6,  6,  2,  s3_time + INTERVAL '6 days', (SELECT id FROM cards WHERE deck_id = d4 AND front = 'Enero'),           uid, s3),
        (5, 2.5,  15, 3,  s3_time + INTERVAL '15 days',(SELECT id FROM cards WHERE deck_id = d4 AND front = 'Hoy'),             uid, s3),
        (5, 2.6,  20, 4,  s3_time + INTERVAL '20 days',(SELECT id FROM cards WHERE deck_id = d4 AND front = 'Mañana'),          uid, s3),
        (4, 2.5,  25, 4,  s3_time + INTERVAL '25 days',(SELECT id FROM cards WHERE deck_id = d4 AND front = 'Ayer'),            uid, s3),
        (2, 1.96, 1,  0,  s3_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d5 AND front = 'Padre'),           uid, s3),
        (3, 2.36, 1,  1,  s3_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d5 AND front = 'Hermano'),         uid, s3),
        (2, 2.18, 1,  0,  s3_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d5 AND front = 'Hermana'),         uid, s3),
        (2, 2.18, 1,  0,  s3_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d5 AND front = 'Hijo'),            uid, s3),
        (4, 2.36, 6,  2,  s3_time + INTERVAL '6 days', (SELECT id FROM cards WHERE deck_id = d5 AND front = 'Hija'),            uid, s3),
        (1, 1.96, 1,  0,  s3_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d5 AND front = 'Amigo'),           uid, s3),
        (2, 2.18, 1,  0,  s3_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d5 AND front = 'Amiga'),           uid, s3);

    -- Session 4 (1 day ago, 14 reviews) — Deck 5 + Deck 6 + Deck 3
    INSERT INTO card_review_log (quality, easiness_factor, interval_days, repetition_count, next_review_date, card_id, user_id, session_id)
    VALUES
        (4, 2.5,  15, 3,  s4_time + INTERVAL '15 days',(SELECT id FROM cards WHERE deck_id = d5 AND front = 'Niño'),            uid, s4),
        (3, 2.36, 15, 3,  s4_time + INTERVAL '15 days',(SELECT id FROM cards WHERE deck_id = d5 AND front = 'Niña'),            uid, s4),
        (5, 2.6,  25, 4,  s4_time + INTERVAL '25 days',(SELECT id FROM cards WHERE deck_id = d5 AND front = 'Persona'),         uid, s4),
        (2, 2.18, 1,  0,  s4_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d6 AND front = 'Oficina'),         uid, s4),
        (3, 2.36, 1,  1,  s4_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d6 AND front = 'Jefe'),            uid, s4),
        (4, 2.5,  1,  1,  s4_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d6 AND front = 'Reunión'),         uid, s4),
        (4, 2.5,  6,  2,  s4_time + INTERVAL '6 days', (SELECT id FROM cards WHERE deck_id = d6 AND front = 'Teléfono'),        uid, s4),
        (4, 2.6,  6,  2,  s4_time + INTERVAL '6 days', (SELECT id FROM cards WHERE deck_id = d6 AND front = 'Correo'),          uid, s4),
        (5, 2.5,  15, 3,  s4_time + INTERVAL '15 days',(SELECT id FROM cards WHERE deck_id = d6 AND front = 'Escritorio'),      uid, s4),
        (3, 2.5,  15, 3,  s4_time + INTERVAL '15 days',(SELECT id FROM cards WHERE deck_id = d6 AND front = 'Ordenador'),       uid, s4),
        (4, 2.6,  20, 4,  s4_time + INTERVAL '20 days',(SELECT id FROM cards WHERE deck_id = d6 AND front = 'Documento'),       uid, s4),
        (4, 2.6,  30, 5,  s4_time + INTERVAL '30 days',(SELECT id FROM cards WHERE deck_id = d6 AND front = 'Salario'),         uid, s4),
        (4, 2.5,  15, 3,  s4_time + INTERVAL '15 days',(SELECT id FROM cards WHERE deck_id = d3 AND front = 'Mapa'),            uid, s4),
        (5, 2.6,  15, 3,  s4_time + INTERVAL '15 days',(SELECT id FROM cards WHERE deck_id = d3 AND front = 'Pasaporte'),       uid, s4);

    -- Session 5 (today, 13 reviews) — Deck 3 + Deck 7
    INSERT INTO card_review_log (quality, easiness_factor, interval_days, repetition_count, next_review_date, card_id, user_id, session_id)
    VALUES
        (4, 2.6,  20, 4,  s5_time + INTERVAL '20 days',(SELECT id FROM cards WHERE deck_id = d3 AND front = 'Equipaje'),        uid, s5),
        (2, 2.18, 1,  0,  s5_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d7 AND front = 'Tener'),           uid, s5),
        (2, 2.18, 1,  0,  s5_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d7 AND front = 'Hacer'),           uid, s5),
        (4, 2.5,  1,  1,  s5_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d7 AND front = 'Decir'),           uid, s5),
        (4, 2.6,  1,  1,  s5_time + INTERVAL '1 day',  (SELECT id FROM cards WHERE deck_id = d7 AND front = 'Ir'),              uid, s5),
        (3, 2.36, 6,  2,  s5_time + INTERVAL '6 days', (SELECT id FROM cards WHERE deck_id = d7 AND front = 'Ver'),             uid, s5),
        (4, 2.5,  6,  2,  s5_time + INTERVAL '6 days', (SELECT id FROM cards WHERE deck_id = d7 AND front = 'Saber'),           uid, s5),
        (4, 2.5,  15, 3,  s5_time + INTERVAL '15 days',(SELECT id FROM cards WHERE deck_id = d7 AND front = 'Poder'),           uid, s5),
        (2, 2.36, 15, 3,  s5_time + INTERVAL '15 days',(SELECT id FROM cards WHERE deck_id = d7 AND front = 'Querer'),          uid, s5),
        (5, 2.6,  20, 4,  s5_time + INTERVAL '20 days',(SELECT id FROM cards WHERE deck_id = d7 AND front = 'Gustar'),          uid, s5),
        (4, 2.5,  20, 4,  s5_time + INTERVAL '20 days',(SELECT id FROM cards WHERE deck_id = d7 AND front = 'Comer'),           uid, s5),
        (3, 2.36, 25, 4,  s5_time + INTERVAL '25 days',(SELECT id FROM cards WHERE deck_id = d7 AND front = 'Beber'),           uid, s5),
        (5, 2.6,  30, 5,  s5_time + INTERVAL '30 days',(SELECT id FROM cards WHERE deck_id = d7 AND front = 'Dormir'),          uid, s5);

END $$;
