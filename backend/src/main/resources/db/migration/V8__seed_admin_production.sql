-- V8__seed_admin_production.sql
--
-- Creates the initial admin user for production.
-- ON CONFLICT DO NOTHING — safe to re-run.

INSERT INTO users (id, email, password_hash, full_name, role, status, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'admin@callsagents.com',
    '$2b$10$aIOArdvrDwK6ba/xsGqCRu0Di6DId49C6IaZYu4Cy.Gx5EIY3sbTy',
    'Admin',
    'ADMIN',
    'ACTIVE',
    NOW(),
    NOW()
)
ON CONFLICT (email) DO NOTHING;
