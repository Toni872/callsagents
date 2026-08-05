package com.callsagents.backend.auth.security;

import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.text.ParseException;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public JwtAuthenticationFilter(JwtService jwtService, RefreshTokenService refreshTokenService) {
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<JWTClaimsSet> maybeClaims = jwtService.parseToken(token);
        if (maybeClaims.isEmpty()) {
            log.debug("Invalid or expired JWT presented on {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        JWTClaimsSet claims = maybeClaims.get();

        if (jwtService.isRefreshToken(claims)) {
            log.debug("Refresh token presented on protected endpoint {}; ignoring for auth context",
                request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        String jti = claims.getJWTID();
        if (jti != null && refreshTokenService.isAccessTokenRevoked(jti)) {
            log.debug("Access token {} is revoked; ignoring", jti);
            filterChain.doFilter(request, response);
            return;
        }

        String email;
        String role;
        try {
            email = claims.getStringClaim(JwtService.CLAIM_EMAIL);
            role = claims.getStringClaim(JwtService.CLAIM_ROLE);
        } catch (ParseException e) {
            log.debug("JWT missing email/role claims: {}", e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }
        if (email == null || role == null) {
            filterChain.doFilter(request, response);
            return;
        }

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            email,
            null,
            java.util.List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }
}
