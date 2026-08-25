-- V14: Update admin password (admin123 → Calls@gents2025!)
-- Generated BCrypt hash with rounds=12 for strong security

UPDATE users
SET password_hash = '$2b$12$uwCD0wKItfJFsWszLT5MjuX8Ss/eOxb6.fLZuPtk0VZ58esIlc1mm',
    updated_at = NOW()
WHERE email = 'contact@script-9.com';
