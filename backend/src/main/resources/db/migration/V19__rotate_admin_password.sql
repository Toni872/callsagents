-- Rotate the production admin password. The plaintext is intentionally NOT
-- stored anywhere in the repo: it lives only in secrets/env as
-- CALLSAGENTS_ADMIN_PASSWORD (see scripts/verify-deploy.ps1).
UPDATE users
   SET password_hash = '$2a$12$2oYN8/ACvxyMm8CTDP/xYeuAT1uZhjRIz4xUDJ1eSI6NvRm6C0yLK'
 WHERE email = 'contact@script-9.com';