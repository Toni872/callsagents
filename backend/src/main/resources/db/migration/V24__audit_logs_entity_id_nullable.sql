-- audit_logs.entity_id becomes nullable to support bulk operations
-- (e.g. CSV lead import) that log a single audit event without a single entity id.
ALTER TABLE audit_logs ALTER COLUMN entity_id DROP NOT NULL;