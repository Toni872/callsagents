-- Multi-tenant scoping: every lead now belongs to the authenticated user that
-- owns it (tenant = user). Backfill pre-existing (orphan) leads to a
-- deterministic owner so nothing becomes invisible after the migration:
--   1) the user with the oldest business profile,
--   2) the production admin (contact@script-9.com),
--   3) the dev seeds as last resort.
ALTER TABLE leads ADD COLUMN created_by UUID REFERENCES users(id);

UPDATE leads
   SET created_by = COALESCE(
       (SELECT bp.user_id FROM business_profiles bp
         ORDER BY bp.created_at ASC, bp.id ASC LIMIT 1),
       (SELECT id FROM users WHERE email = 'contact@script-9.com' LIMIT 1),
       (SELECT id FROM users WHERE email = 'admin@callsagents.local' LIMIT 1),
       (SELECT id FROM users WHERE email = 'admin@callsagents.com' LIMIT 1)
   )
 WHERE created_by IS NULL;

ALTER TABLE leads ALTER COLUMN created_by SET NOT NULL;

CREATE INDEX idx_leads_created_by ON leads(created_by);