-- La contraseña es DEV_admin_99%%
INSERT INTO users (username,
                   email,
                   password_hash,
                   zone_info,
                   last_login,
                   last_notification_sent,
                   session_threshold,
                   start_of_day,
                   notifications_enabled,
                   created_at,
                   updated_at)
VALUES ('${ADMIN_USERNAME}',
        '${ADMIN_EMAIL}',
        '${ADMIN_PSW_HASH}',
        '${ADMIN_ZONE}',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        30,
        6,
        TRUE,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);

-- Asignar los roles al administrador
INSERT INTO user_roles (user_id, roles)
VALUES ((SELECT id FROM users WHERE username = '${ADMIN_USERNAME}'), 'ROLE_ADMIN');

INSERT INTO user_roles (user_id, roles)
VALUES ((SELECT id FROM users WHERE username = '${ADMIN_USERNAME}'), 'ROLE_USER');
