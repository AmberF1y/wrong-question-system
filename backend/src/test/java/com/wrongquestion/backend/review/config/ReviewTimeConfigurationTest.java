package com.wrongquestion.backend.review.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReviewTimeConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(ReviewTimeConfiguration.class);

    @Test
    void shouldUseAsiaShanghaiByDefault() {
        contextRunner.run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(
                    ZoneId.of("Asia/Shanghai"),
                    context.getBean(Clock.class).getZone()
            );
        });
    }

    @Test
    void shouldFailContextStartupForInvalidZoneId() {
        contextRunner
                .withPropertyValues("app.review.zone-id=Invalid/Zone")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void shouldCalculateBusinessDateInConfiguredZone() {
        Instant instant = Instant.parse("2026-09-03T16:30:00Z");
        Clock utcClock = Clock.fixed(instant, ZoneOffset.UTC);
        Clock shanghaiClock = Clock.fixed(
                instant,
                ZoneId.of("Asia/Shanghai")
        );

        assertEquals(LocalDate.of(2026, 9, 3), LocalDate.now(utcClock));
        assertEquals(
                LocalDate.of(2026, 9, 4),
                LocalDate.now(shanghaiClock)
        );
    }
}
