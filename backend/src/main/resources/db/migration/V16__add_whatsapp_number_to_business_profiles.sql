-- Add whatsapp_number column to business_profiles for WhatsApp multi-tenancy
ALTER TABLE business_profiles ADD COLUMN whatsapp_number VARCHAR(20);

-- Index for fast lookup by whatsapp number (webhook routing)
CREATE INDEX idx_business_profiles_whatsapp_number ON business_profiles (whatsapp_number) WHERE whatsapp_number IS NOT NULL;
