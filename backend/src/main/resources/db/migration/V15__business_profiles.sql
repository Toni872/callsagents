CREATE TABLE business_profiles (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL UNIQUE,
    company_name        VARCHAR(255) NOT NULL DEFAULT '',
    website             VARCHAR(500),
    industry            VARCHAR(100),
    services            TEXT,
    tone                VARCHAR(20) DEFAULT 'professional',
    bot_name            VARCHAR(100) DEFAULT 'Naiara',
    greeting            TEXT,
    chat_color          VARCHAR(7) DEFAULT '#25D366',
    onboarding_complete BOOLEAN DEFAULT FALSE,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_business_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
