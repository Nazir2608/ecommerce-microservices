-- V2__seed_admin_user.sql
--
-- Seed a default admin user for development.
-- Password: Admin@12345  (BCrypt hash below)
-- IMPORTANT: Change this password immediately in any non-local environment.

INSERT INTO users (id, username, email, password, first_name, last_name, status, email_verified)
VALUES (
    uuid_generate_v4(),
    'admin',
    'admin@ecommerce.com',
    -- BCrypt hash of 'Admin@12345' with strength=12
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewrDMlEAbSp/6tm.',
    'System',
    'Admin',
    'ACTIVE',
    TRUE
)
ON CONFLICT (email) DO NOTHING;   -- idempotent — safe to run multiple times

-- Assign ADMIN role to the seed admin user
INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_ADMIN'
FROM users
WHERE email = 'admin@ecommerce.com'
ON CONFLICT DO NOTHING;
