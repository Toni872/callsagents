ALTER TABLE leads ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE INDEX idx_leads_deleted_at ON leads (deleted_at);
