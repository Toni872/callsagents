package com.callsagents.backend.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;
    private final boolean springdocEnabled;
    private final List<String> corsAllowedOrigins;

    public SecurityConfig(CustomUserDetailsService customUserDetailsService,
                          JwtAuthenticationFilter jwtAuthenticationFilter,
                          ObjectMapper objectMapper,
                          @Value("${springdoc.api-docs.enabled:true}") boolean springdocEnabled,
                          @Value("${app.cors.allowed-origins:}") List<String> corsAllowedOrigins) {
        this.customUserDetailsService = customUserDetailsService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
        this.springdocEnabled = springdocEnabled;
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/auth/login", "/auth/refresh", "/auth/register", "/auth/google").permitAll();
                auth.requestMatchers("/health").permitAll();
                auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                // Webhooks de providers de voz (Vapi, Retell). Sin auth en la capa
                // de seguridad a propósito: el handler verifica la firma del provider
                // (Retell HMAC-SHA256, Vapi X-Vapi-Secret) y devuelve 401 si no cuadra.
                auth.requestMatchers("/voice/webhook/**").permitAll();
                // WhatsApp webhook (Vonage sandbox): sin auth, Vonage llama directamente.
                auth.requestMatchers("/webhooks/vonage").permitAll();
                auth.requestMatchers("/webhooks/vonage/**").permitAll();
                // Chat widget API: sin auth, el widget del sitio web llama directamente.
                auth.requestMatchers("/chat/**").permitAll();
                // Widget config publico: sin auth, el widget embebido carga branding.
                auth.requestMatchers("/business/profile/widget-config/**").permitAll();
                // Demo voice call: sin auth, el demo público crea web calls de Retell.
                auth.requestMatchers("/voice/web-call").permitAll();
                // Callback OAuth de calendario: Google redirige el navegador aquí
                // SIN Authorization header (es una navegación de browser). Es seguro
                // exponerlo: solo intercambia un code de un solo uso que solo quien
                // completó el consentimiento en Google puede obtener, y el state
                // (email del usuario Callsagents) atribuye la integración.
                auth.requestMatchers("/calendar/integrations/*/callback").permitAll();
                // Swagger UI / OpenAPI docs (Fase 8): solo cuando SpringDoc está
                // habilitado. En producción (SPRINGDOC_ENABLED=false) se deniega de
                // forma explícita para que nada del contrato quede expuesto.
                if (springdocEnabled) {
                    auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                } else {
                    auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").denyAll();
                }
                auth.anyRequest().authenticated();
            })
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint((req, res, ex) -> writeError(res, HttpServletResponse.SC_UNAUTHORIZED,
                    "unauthorized", ex.getMessage(), req.getRequestURI()))
                .accessDeniedHandler((req, res, ex) -> writeError(res, HttpServletResponse.SC_FORBIDDEN,
                    "forbidden", ex.getMessage(), req.getRequestURI()))
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /**
     * CORS para desarrollo local. En producción el frontend se sirve en el mismo
     * origin (nginx proxy), así que la lista queda vacía y no se permite ningún
     * origin externo. En dev se permite el dev server de Angular (localhost:4200).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsAllowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(customUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    private void writeError(HttpServletResponse res, int status, String error, String message, String path)
            throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = Map.of(
            "timestamp", Instant.now().toString(),
            "status", status,
            "error", error,
            "message", message == null ? "" : message,
            "path", path
        );
        objectMapper.writeValue(res.getWriter(), body);
    }
}
