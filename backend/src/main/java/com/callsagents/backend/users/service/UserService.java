package com.callsagents.backend.users.service;

import com.callsagents.backend.auth.entity.User;
import com.callsagents.backend.auth.entity.UserRole;
import com.callsagents.backend.auth.entity.UserStatus;
import com.callsagents.backend.auth.repository.UserRepository;
import com.callsagents.backend.common.exception.BadRequestException;
import com.callsagents.backend.common.exception.ResourceNotFoundException;
import com.callsagents.backend.users.dto.CreateUserRequest;
import com.callsagents.backend.users.dto.UserListItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * User management service — admin operations on user accounts.
 *
 * What this service does NOT do (out of scope for the current iteration):
 *  - Edit existing users (full update)
 *  - Delete users (hard or soft)
 *  - Roles beyond the 3 base ones (no custom roles / permissions)
 *  - Password reset flow (no email link / token)
 *  - Invitation email sending
 *
 * Those features are intentionally deferred to keep this iteration focused.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * List users with pagination, optionally filtered by role.
     */
    @Transactional(readOnly = true)
    public Page<UserListItem> listUsers(UserRole role, Pageable pageable) {
        Page<User> page = (role != null)
            ? userRepository.findByRole(role, pageable)
            : userRepository.findAll(pageable);

        return page.map(this::toListItem);
    }

    /**
     * Create a new user. Admin provides email + cleartext password (we hash it
     * here — never persisted as plaintext). Email must be unique.
     *
     * @throws BadRequestException if email already exists
     */
    @Transactional
    public User createUser(CreateUserRequest req) {
        Optional<User> existing = userRepository.findByEmail(req.email());
        if (existing.isPresent()) {
            // Don't leak whether the email was registered — same message either way,
            // but for an admin tool we can be specific.
            throw new BadRequestException(
                "A user with email '" + req.email() + "' already exists"
            );
        }

        User user = User.builder()
            .email(req.email())
            .passwordHash(passwordEncoder.encode(req.password()))
            .fullName(req.fullName())
            .role(req.role())
            .status(UserStatus.ACTIVE)
            .build();

        User saved = userRepository.save(user);
        log.info("Created user {} with role {}", saved.getEmail(), saved.getRole());
        return saved;
    }

    /**
     * Find user by id. Used internally for now; will be needed for
     * future GET /api/users/{id} endpoint.
     */
    @Transactional(readOnly = true)
    public User findById(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    /**
     * Enable or disable a user account (ADMIN only).
     *
     * Guard rails:
     *  - An admin cannot disable their own account.
     *  - The last active admin cannot be disabled (would lock everyone out).
     *
     * @throws BadRequestException for self-disable or disabling the last active admin
     * @throws ResourceNotFoundException if the user does not exist
     */
    @Transactional
    public User updateStatus(UUID id, UserStatus newStatus, String currentEmail) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        if (newStatus == UserStatus.DISABLED) {
            if (user.getEmail().equals(currentEmail)) {
                throw new BadRequestException("You cannot disable your own account");
            }
            if (user.getRole() == UserRole.ADMIN && user.getStatus() == UserStatus.ACTIVE) {
                long activeAdmins = userRepository.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE);
                if (activeAdmins <= 1) {
                    throw new BadRequestException("Cannot disable the last active admin");
                }
            }
        }

        user.setStatus(newStatus);
        User saved = userRepository.save(user);
        log.info("Set status {} for user {}", saved.getStatus(), saved.getEmail());
        return saved;
    }

    private UserListItem toListItem(User u) {
        return new UserListItem(
            u.getId(),
            u.getEmail(),
            u.getFullName(),
            u.getRole(),
            u.getStatus(),
            u.getLastLoginAt(),
            u.getCreatedAt()
        );
    }
}
