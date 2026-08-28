package com.callsagents.backend.whatsapp.service;

import com.callsagents.backend.leads.repository.LeadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WhatsAppServiceTest {

    @Mock
    private LeadRepository leadRepository;

    private WhatsAppService service;

    private static final String PHONE = "whatsapp:+34687723287";
    private static final UUID BUSINESS_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new WhatsAppService(leadRepository);
    }

    @Test
    void initialEmptyMessage_returnsGreeting() {
        String reply = service.processMessage(PHONE, "");
        assertTrue(reply.contains("Hola"));
        assertTrue(reply.contains("asistente"));
    }

    @Test
    void initialText_returnsServiceQuestion() {
        String reply = service.processMessage(PHONE, "Automatización de ventas");
        assertTrue(reply.toLowerCase().contains("automatización de ventas"));
        assertTrue(reply.contains("nombre"));
    }

    @Test
    void fullFlow_greetingToQualification() {
        // Step 1: Initial message → greeting
        String r1 = service.processMessage(PHONE, "", BUSINESS_ID);
        assertTrue(r1.contains("asistente"));

        // Step 2: Service interest → asks for name
        String r2 = service.processMessage(PHONE, "Chatbot para mi negocio", BUSINESS_ID);
        assertTrue(r2.contains("nombre"));

        // Step 3: Name → asks for email
        String r3 = service.processMessage(PHONE, "Antonio García", BUSINESS_ID);
        assertTrue(r3.contains("email"));

        // Step 4: Email → qualification complete
        String r4 = service.processMessage(PHONE, "antonio@test.com", BUSINESS_ID);
        assertTrue(r4.contains("registrado") || r4.contains("contactaremos"));

        // Lead should have been saved with the resolved owner
        verify(leadRepository).save(argThat(lead -> BUSINESS_ID.equals(((com.callsagents.backend.leads.entity.Lead) lead).getCreatedBy())));
    }

    @Test
    void fullFlow_withoutOwner_skipsLeadSave() {
        // When no business profile resolves (businessId null), the conversation
        // still completes but no lead is persisted (created_by NOT NULL since V18).
        String r1 = service.processMessage(PHONE, "");
        String r2 = service.processMessage(PHONE, "Chatbot para mi negocio");
        String r3 = service.processMessage(PHONE, "Antonio García");
        String r4 = service.processMessage(PHONE, "antonio@test.com");
        assertTrue(r4.contains("registrado") || r4.contains("contactaremos"));
        verify(leadRepository, never()).save(any());
    }

    @Test
    void callRequest_atAnyStep_returnsCallResponse() {
        // Start conversation
        service.processMessage(PHONE, "Automatización");

        // Request a call
        String reply = service.processMessage(PHONE, "Quiero una llamada");
        assertTrue(reply.contains("llamaremos") || reply.contains("llamada"));
    }

    @Test
    void resetAtAnyStep_returnsGoodbye() {
        service.processMessage(PHONE, "Automatización");

        String reply = service.processMessage(PHONE, "hola");
        assertTrue(reply.contains("Hasta pronto"));
    }

    @Test
    void invalidEmail_asksAgain() {
        service.processMessage(PHONE, "Automatización");
        service.processMessage(PHONE, "Antonio");

        String reply = service.processMessage(PHONE, "no-tengo-email");
        assertTrue(reply.contains("email válido"));
    }

    @Test
    void completedState_handlesFollowUp() {
        // Complete the full flow
        service.processMessage(PHONE, "");
        service.processMessage(PHONE, "Ventas");
        service.processMessage(PHONE, "Antonio");
        service.processMessage(PHONE, "antonio@test.com");

        // After completion, follow-up should get a generic response
        String reply = service.processMessage(PHONE, "Gracias");
        assertNotNull(reply);
        assertFalse(reply.isEmpty());
    }
}
