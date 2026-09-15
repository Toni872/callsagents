-- Web calls (WebRTC via the public /voice/web-call endpoint) have no phone
-- number: the caller connects from their browser through the Retell Web SDK.
-- V6 made phone_number NOT NULL, valid for outbound PSTN calls only. Allow
-- NULL so web-call rows can be persisted (and later updated by webhooks).
ALTER TABLE voice_calls ALTER COLUMN phone_number DROP NOT NULL;