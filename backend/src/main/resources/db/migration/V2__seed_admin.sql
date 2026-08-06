-- V2__seed_admin.sql
-- DEV ONLY seed user. Production deployments should remove or replace this migration.
-- Default credentials: admin@callsagents.local / admin123
-- Password hash is BCrypt strength 10 for "admin123" — generated via Python bcrypt.

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
