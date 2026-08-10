-- V2__seed_admin.sql
--
-- ⚠️  *** DEV ONLY SEED USER ***
--
-- This migration inserts ONE admin user with publicly-known credentials:
--   email:    admin@callsagents.local
--   password: admin123
--
-- This is INTENDED ONLY for development and the initial Docker smoke-test.
--
-- FOR PRODUCTION:
--   1. Skip this migration by setting spring.flyway.locations[0]=classpath:db/migration/V1
--      in production profile, OR delete this V2 file before deploying.
--   2. Create the first real admin via the API after deploy:
--        POST /api/users
--        Authorization: Bearer <a-bootstrap-admin-token>
--        { "email": "...", "password": "...", "fullName": "...", "role": "ADMIN" }
--
-- The default BCrypt hash below corresponds to the password "admin123"
-- (BCrypt strength 10, generated via Python bcrypt 4.x).
--
-- ON CONFLICT (email) DO NOTHING — if migration is re-run against an existing DB,
-- this is a no-op.

INSERT INTO users (id, email, password_hash, full_name, role, status, last_login_at, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'admin@callsagents.local',
    '$2b$10$aIOArdvrDwK6ba/xsGqCRu0Di6DId49C6IaZYu4Cy.Gx5EIY3sbTy',
    'Admin',
    'ADMIN',
    'ACTIVE',
    NULL,
    NOW(),
    NOW()
)
ON CONFLICT (email) DO NOTHING;
