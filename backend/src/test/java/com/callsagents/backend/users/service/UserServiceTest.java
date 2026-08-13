package com.callsagents.backend.users.service;

import com.callsagents.backend.auth.entity.User;
import com.callsagents.backend.auth.entity.UserRole;
import com.callsagents.backend.auth.entity.UserStatus;
import com.callsagents.backend.auth.repository.UserRepository;
import com.callsagents.backend.common.exception.BadRequestException;
import com.callsagents.backend.common.exception.ResourceNotFoundException;
import com.callsagents.backend.users.dto.CreateUserRequest;
import com.callsagents.backend.users.dto.UserListItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;

    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(10);
        userService = new UserService(userRepository, passwordEncoder);
    }

    private CreateUserRequest buildRequest() {
        return new CreateUserRequest(
            "newuser@callsagents.local",
            "securePass123",
            "New User",
            UserRole.AGENT
        );
    }

    private User buildUser(UUID id, String email, UserRole role) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setPasswordHash("$2a$10$hashed");
        u.setFullName("New User");
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        u.setCreatedAt(Instant.now());
        u.setUpdatedAt(Instant.now());
        return u;
    }

    // -------------------- createUser --------------------

    @Test
    @DisplayName("createUser: hashes password, persists, returns saved")
    void createUser_success() {
        CreateUserRequest req = buildRequest();
        when(userRepository.findByEmail(req.email())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        User result = userService.createUser(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        // Password was hashed (NOT plaintext)
        assertThat(saved.getPasswordHash()).isNotEqualTo(req.password());
        assertThat(saved.getPasswordHash()).startsWith("$2a$"); // BCrypt format
        // Other fields forwarded
        assertThat(saved.getEmail()).isEqualTo(req.email());
        assertThat(saved.getFullName()).isEqualTo(req.fullName());
        assertThat(saved.getRole()).isEqualTo(req.role());
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        // Returned object has the id assigned by save
        assertThat(result.getId()).isNotNull();
    }

    @Test
    @DisplayName("createUser: duplicate email throws BadRequestException")
    void createUser_duplicateEmail() {
        CreateUserRequest req = buildRequest();
        when(userRepository.findByEmail(req.email())).thenReturn(
            Optional.of(buildUser(UUID.randomUUID(), req.email(), UserRole.AGENT))
        );

        assertThatThrownBy(() -> userService.createUser(req))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("createUser: with ADMIN role is allowed")
    void createUser_adminRole() {
        CreateUserRequest req = new CreateUserRequest(
            "newadmin@callsagents.local", "securePass123", "New Admin", UserRole.ADMIN
        );
        when(userRepository.findByEmail(req.email())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        User result = userService.createUser(req);

        assertThat(result.getRole()).isEqualTo(UserRole.ADMIN);
    }

    // -------------------- listUsers --------------------

    @Test
    @DisplayName("listUsers: returns page of UserListItems")
    void listUsers_success() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Page<User> users = new PageImpl<>(List.of(
            buildUser(id1, "a@x.com", UserRole.ADMIN),
            buildUser(id2, "b@x.com", UserRole.AGENT)
        ));
        when(userRepository.findAll(any(Pageable.class))).thenReturn(users);

        Page<UserListItem> result = userService.listUsers(null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).email()).isEqualTo("a@x.com");
        assertThat(result.getContent().get(0).role()).isEqualTo(UserRole.ADMIN);
        assertThat(result.getContent().get(1).email()).isEqualTo("b@x.com");
        assertThat(result.getContent().get(1).role()).isEqualTo(UserRole.AGENT);
    }

    @Test
    @DisplayName("listUsers: empty page returns empty content")
    void listUsers_empty() {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        Page<UserListItem> result = userService.listUsers(null, PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    // -------------------- findById --------------------

    @Test
    @DisplayName("findById: returns user when found")
    void findById_success() {
        UUID id = UUID.randomUUID();
        User u = buildUser(id, "x@y.com", UserRole.AGENT);
        when(userRepository.findById(id)).thenReturn(Optional.of(u));

        User result = userService.findById(id);

        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getEmail()).isEqualTo("x@y.com");
    }

    @Test
    @DisplayName("findById: throws ResourceNotFoundException when missing")
    void findById_missing() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(id))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("not found");
    }

    // -------------------- updateStatus --------------------

    @Test
    @DisplayName("updateStatus: sets DISABLED and persists")
    void updateStatus_disable_success() {
        UUID id = UUID.randomUUID();
        User u = buildUser(id, "agent@x.com", UserRole.AGENT);
        when(userRepository.findById(id)).thenReturn(Optional.of(u));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.updateStatus(id, UserStatus.DISABLED, "admin@x.com");

        assertThat(result.getStatus()).isEqualTo(UserStatus.DISABLED);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(UserStatus.DISABLED);
    }

    @Test
    @DisplayName("updateStatus: disabling own account throws BadRequestException")
    void updateStatus_selfDisable_throws() {
        UUID id = UUID.randomUUID();
        User u = buildUser(id, "admin@x.com", UserRole.ADMIN);
        when(userRepository.findById(id)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> userService.updateStatus(id, UserStatus.DISABLED, "admin@x.com"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("cannot disable your own");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("updateStatus: disabling the last active admin throws BadRequestException")
    void updateStatus_lastActiveAdmin_throws() {
        UUID id = UUID.randomUUID();
        User u = buildUser(id, "admin@x.com", UserRole.ADMIN);
        when(userRepository.findById(id)).thenReturn(Optional.of(u));
        when(userRepository.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> userService.updateStatus(id, UserStatus.DISABLED, "someoneelse@x.com"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("last active admin");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("updateStatus: unknown id throws ResourceNotFoundException")
    void updateStatus_missing_throws() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateStatus(id, UserStatus.DISABLED, "admin@x.com"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("updateStatus: enabling a disabled user works")
    void updateStatus_enable_success() {
        UUID id = UUID.randomUUID();
        User u = buildUser(id, "agent@x.com", UserRole.AGENT);
        u.setStatus(UserStatus.DISABLED);
        when(userRepository.findById(id)).thenReturn(Optional.of(u));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.updateStatus(id, UserStatus.ACTIVE, "admin@x.com");

        assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }
}
