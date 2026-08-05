package com.callsagents.backend.auth.controller;

import com.callsagents.backend.auth.dto.LoginRequest;
import com.callsagents.backend.auth.dto.LoginResponse;
import com.callsagents.backend.auth.dto.RefreshRequest;
import com.callsagents.backend.auth.dto.RefreshResponse;
import com.callsagents.backend.auth.dto.UserDto;
import com.callsagents.backend.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ResponseEntity.ok(authService.refresh(req));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        @RequestHeader("Authorization") String authHeader,
        @RequestBody(required = false) RefreshRequest req
    ) {
        String accessToken = authHeader == null ? null : authHeader.replace("Bearer ", "").trim();
        String refreshToken = req != null ? req.refreshToken() : null;
        authService.logout(accessToken, refreshToken);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(authService.getCurrentUser(user.getUsername()));
    }
}
