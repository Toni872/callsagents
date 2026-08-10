package com.callsagents.backend.auth.security;

import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   RefreshTokenService refreshTokenService,
                                   UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userDetailsService = userDetailsService;
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
        try {
            email = claims.getStringClaim(JwtService.CLAIM_EMAIL);
        } catch (ParseException e) {
            log.debug("JWT missing email claim: {}", e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }
        if (email == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Hydrate a real UserDetails so @AuthenticationPrincipal works in controllers.
        // The previous implementation set the raw email string as principal, which made
        // @AuthenticationPrincipal UserDetails resolve to null in any endpoint that
        // needed the current user (e.g. LeadController.create).
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(email);
        } catch (UsernameNotFoundException | DisabledException ex) {
            log.debug("User {} not available for auth context: {}", email, ex.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            userDetails,
            null,
            userDetails.getAuthorities()
        );
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }
}