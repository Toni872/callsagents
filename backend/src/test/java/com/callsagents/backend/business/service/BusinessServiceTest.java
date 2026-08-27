package com.callsagents.backend.business.service;

import com.callsagents.backend.auth.entity.User;
import com.callsagents.backend.auth.entity.UserRole;
import com.callsagents.backend.auth.entity.UserStatus;
import com.callsagents.backend.auth.repository.UserRepository;
import com.callsagents.backend.business.dto.BusinessProfileRequest;
import com.callsagents.backend.business.dto.BusinessProfileResponse;
import com.callsagents.backend.business.dto.WidgetConfigResponse;
import com.callsagents.backend.business.entity.BusinessProfile;
import com.callsagents.backend.business.repository.BusinessProfileRepository;
import com.callsagents.backend.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessServiceTest {

    @Mock
    private BusinessProfileRepository profileRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BusinessService businessService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
            .id(userId)
            .email("test@example.com")
            .fullName("Test User")
            .role(UserRole.AGENT)
            .status(UserStatus.ACTIVE)
            .passwordHash("x")
            .build();
    }

    @Test
    void createSetsDefaultsAndReturnsResponse() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(profileRepository.save(any(BusinessProfile.class))).thenAnswer(inv -> {
            BusinessProfile p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        BusinessProfileResponse response = businessService.create(userId);

        assertNotNull(response);
        assertEquals("", response.companyName());
        assertFalse(response.onboardingComplete());
        verify(profileRepository).save(any(BusinessProfile.class));
    }

    @Test
    void createThrowsWhenUserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> businessService.create(userId));
        verify(profileRepository, never()).save(any());
    }

    @Test
    void getByUserIdReturnsResponseWhenFound() {
        BusinessProfile profile = sampleProfile();
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

        BusinessProfileResponse response = businessService.getByUserId(userId);

        assertNotNull(response);
        assertEquals("Acme Inc", response.companyName());
        assertEquals("Naiara", response.botName());
    }

    @Test
    void getByUserIdThrowsWhenNotFound() {
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> businessService.getByUserId(userId));
    }

    @Test
    void getProfileEntityByUserIdReturnsNullWhenNotFound() {
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.empty());

        BusinessProfile result = businessService.getProfileEntityByUserId(userId);

        assertEquals(null, result);
    }

    @Test
    void updateAppliesProvidedFields() {
        BusinessProfile profile = sampleProfile();
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(profileRepository.save(any(BusinessProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        BusinessProfileRequest req = new BusinessProfileRequest(
            "New Corp", "https://new.com", "Tech", "Web dev",
            "amigable", "Botty", "Hola!", "#FF0000",
            null, null, null, null
        );

        BusinessProfileResponse response = businessService.update(userId, req);

        assertEquals("New Corp", response.companyName());
        assertEquals("Botty", response.botName());
        assertEquals("amigable", response.tone());
        assertEquals("#FF0000", response.chatColor());
        assertTrue(response.onboardingComplete());
    }

    @Test
    void updateThrowsWhenNotFound() {
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.empty());

        BusinessProfileRequest req = new BusinessProfileRequest("X", null, null, null, null, null, null, null, null, null, null, null);
        assertThrows(ResourceNotFoundException.class, () -> businessService.update(userId, req));
    }

    @Test
    void getWidgetConfigReturnsDefaultsWhenNoProfile() {
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.empty());

        WidgetConfigResponse config = businessService.getWidgetConfig(userId);

        assertEquals("Naiara", config.botName());
        assertNotNull(config.greeting());
        assertEquals("#25D366", config.chatColor());
    }

    @Test
    void getWidgetConfigReturnsProfileValues() {
        BusinessProfile profile = sampleProfile();
        profile.setBotName("CustomBot");
        profile.setChatColor("#ABCDEF");
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

        WidgetConfigResponse config = businessService.getWidgetConfig(userId);

        assertEquals("CustomBot", config.botName());
        assertEquals("#ABCDEF", config.chatColor());
    }

    @Test
    void markOnboardingCompleteSetsFlag() {
        BusinessProfile profile = sampleProfile();
        assertFalse(profile.getOnboardingComplete());
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(profileRepository.save(any(BusinessProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        businessService.markOnboardingComplete(userId);

        assertTrue(profile.getOnboardingComplete());
    }

    @Test
    void markOnboardingCompleteThrowsWhenNotFound() {
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> businessService.markOnboardingComplete(userId));
    }

    private BusinessProfile sampleProfile() {
        return BusinessProfile.builder()
            .id(UUID.randomUUID())
            .user(user)
            .companyName("Acme Inc")
            .tone("profesional")
            .botName("Naiara")
            .chatColor("#25D366")
            .onboardingComplete(false)
            .build();
    }
}
