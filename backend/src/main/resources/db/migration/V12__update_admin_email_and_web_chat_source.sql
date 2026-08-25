-- V12: Update admin email and add WEB_CHAT lead source
--
-- 1. Update admin email to contact@script-9.com
UPDATE users SET email = 'contact@script-9.com', updated_at = NOW()
WHERE email = 'admin@callsagents.com' AND role = 'ADMIN';

-- 2. Add WEB_CHAT to lead_source enum
ALTER TYPE lead_source ADD VALUE IF NOT EXISTS 'WEB_CHAT';
