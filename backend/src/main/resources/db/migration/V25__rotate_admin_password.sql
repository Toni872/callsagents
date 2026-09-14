-- V25: Rotate the production admin password to a strong random value.
--
-- The plaintext is NOT stored anywhere in this repository. It lives only in the
-- Railway secret CALLSAGENTS_ADMIN_PASSWORD (and in password managers of the
-- maintainers). Previous passwords published in V8/V14 history must no longer
-- grant access: V14's Calls@gents2025! and V8's seed both become invalid after
-- this migration runs.
--
-- This file was migrated by `scripts/verify-deploy.ps1` which reads the
-- plaintext from the environment variable to run the login smoke test.

UPDATE users
   SET password_hash = '$2b$12$hXuDlXLr18GoHXLo.nnoGOc67Jhw2DueLAaMDOGijC.eODGKN2Gia',
       updated_at = NOW()
 WHERE email = 'contact@script-9.com';