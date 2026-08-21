-- V11: Add WHATSAPP to lead_source enum for WhatsApp chatbot integration
ALTER TYPE lead_source ADD VALUE IF NOT EXISTS 'WHATSAPP';
