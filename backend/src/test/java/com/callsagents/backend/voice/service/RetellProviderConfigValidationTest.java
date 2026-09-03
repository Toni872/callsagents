package com.callsagents.backend.voice.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class RetellProviderConfigValidationTest {

    private RetellProvider provider;

    @BeforeEach
    void setUp() {
        provider = new RetellProvider();
    }

    private ListAppender<ILoggingEvent> attachAppender() {
        ch.qos.logback.classic.Logger logger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(RetellProvider.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    @Test
    @DisplayName("validateConfig: WARNs explicitly for each missing RETELL_* var when blank")
    void validateConfig_blankFromNumber_logsWarning() {
        // All values blank (default) -> validateConfig should warn for every var
        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            provider.validateConfig();

            assertThat(appender.list)
                .anyMatch(e -> e.getLevel() == Level.WARN
                    && e.getFormattedMessage().contains(
                        "Voice gate: RETELL_API_KEY is not configured"));
            assertThat(appender.list)
                .anyMatch(e -> e.getLevel() == Level.WARN
                    && e.getFormattedMessage().contains(
                        "Voice gate: RETELL_AGENT_ID is not configured"));
            assertThat(appender.list)
                .anyMatch(e -> e.getLevel() == Level.WARN
                    && e.getFormattedMessage().contains(
                        "Voice gate: RETELL_FROM_NUMBER is not configured"));
        } finally {
            ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(RetellProvider.class))
                .detachAppender(appender);
        }
    }

    @Test
    @DisplayName("validateConfig: fully configured -> no WARN for the provided vars")
    void validateConfig_configured_noWarnings() {
        ReflectionTestUtils.setField(provider, "apiKey", "key_test");
        ReflectionTestUtils.setField(provider, "defaultAgentId", "agent_123");
        ReflectionTestUtils.setField(provider, "defaultFromNumber", "+15550000000");

        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            provider.validateConfig();

            assertThat(appender.list).noneMatch(e ->
                e.getLevel() == Level.WARN
                    && e.getFormattedMessage().contains("Voice gate:"));
        } finally {
            ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(RetellProvider.class))
                .detachAppender(appender);
        }
    }
}