-- Add allowed_domains (comma-separated) to restrict which Referer hosts may
-- call the public chat widget API on behalf of a business. NULL/blank keeps
-- legacy behavior (any domain). Anti-abuse: prevents forged businessId usage
-- from arbitrary sites that pay for Groq calls attributed to the business.
ALTER TABLE business_profiles ADD COLUMN allowed_domains TEXT;