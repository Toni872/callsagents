package com.callsagents.backend.business.service;

import com.callsagents.backend.auth.entity.User;
import com.callsagents.backend.auth.entity.UserRole;
import com.callsagents.backend.auth.entity.UserStatus;
import com.callsagents.backend.business.entity.BusinessProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessPromptComposerTest {

    private BusinessPromptComposer composer;
    private User user;

    @BeforeEach
    void setUp() {
        composer = new BusinessPromptComposer();
        user = User.builder()
            .id(UUID.randomUUID())
            .email("test@example.com")
            .fullName("Test User")
            .role(UserRole.AGENT)
            .status(UserStatus.ACTIVE)
            .passwordHash("x")
            .build();
    }

    @Test
    void composeDefaultReturnsNonNullPrompt() {
        String prompt = composer.composeDefault();

        assertNotNull(prompt);
        assertTrue(prompt.contains("Naiara"));
        assertTrue(prompt.contains("LEAD:"));
    }

    @Test
    void composeReturnsDefaultWhenProfileIsNull() {
        String prompt = composer.compose(null);

        assertNotNull(prompt);
        assertTrue(prompt.contains("Naiara"));
    }

    @Test
    void composeUsesBotNameFromProfile() {
        BusinessProfile profile = BusinessProfile.builder()
            .user(user)
            .companyName("Acme Corp")
            .botName("Roberto")
            .tone("amigable")
            .build();

        String prompt = composer.compose(profile);

        assertTrue(prompt.contains("Roberto"));
        assertFalse(prompt.contains("Naiara"));
    }

    @Test
    void composeIncludesIndustryWhenPresent() {
        BusinessProfile profile = BusinessProfile.builder()
            .user(user)
            .companyName("TechCo")
            .industry("Tecnologia")
            .build();

        String prompt = composer.compose(profile);

        assertTrue(prompt.contains("Tecnologia"));
    }

    @Test
    void composeIncludesServicesWhenPresent() {
        BusinessProfile profile = BusinessProfile.builder()
            .user(user)
            .companyName("TechCo")
            .services("desarrollo web, consultoria")
            .build();

        String prompt = composer.compose(profile);

        assertTrue(prompt.contains("desarrollo web"));
    }

    @Test
    void composeUsesDefaultBotNameWhenBlank() {
        BusinessProfile profile = BusinessProfile.builder()
            .user(user)
            .companyName("TechCo")
            .botName("  ")
            .build();

        String prompt = composer.compose(profile);

        assertTrue(prompt.contains("Naiara"));
    }

    @Test
    void composeUsesDefaultCompanyNameWhenBlank() {
        BusinessProfile profile = BusinessProfile.builder()
            .user(user)
            .companyName("")
            .botName("Botty")
            .build();

        String prompt = composer.compose(profile);

        assertTrue(prompt.contains("Botty"));
        // Blank companyName falls back to "nuestra empresa"
        assertTrue(prompt.contains("nuestra empresa"));
    }

    @Test
    void composeIncludesLeadTag() {
        BusinessProfile profile = BusinessProfile.builder()
            .user(user)
            .companyName("Test")
            .build();

        String prompt = composer.compose(profile);

        assertTrue(prompt.contains("[LEAD:name=NOMBRE|email=EMAIL|service=SERVICIO]"));
    }

    @Test
    void composeRespectsToneFromProfile() {
        BusinessProfile profile = BusinessProfile.builder()
            .user(user)
            .companyName("Test")
            .tone("casual")
            .build();

        String prompt = composer.compose(profile);

        assertTrue(prompt.contains("casual"));
    }

    @Test
    void getDefaultGreetingReturnsNonNull() {
        String greeting = composer.getDefaultGreeting();

        assertNotNull(greeting);
        assertTrue(greeting.length() > 0);
    }
}
