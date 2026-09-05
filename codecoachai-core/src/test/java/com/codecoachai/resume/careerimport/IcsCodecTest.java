package com.codecoachai.resume.careerimport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class IcsCodecTest {

    private final IcsCodec codec = new IcsCodec();

    @Test
    void parsesExportedUtcValuesBackIntoTheEventTimezone() {
        byte[] content = codec.encode(
                List.of(new IcsCodec.IcsExportEvent(
                        "round-trip",
                        "Technical interview",
                        null,
                        null,
                        "CONFIRMED",
                        Instant.parse("2026-07-11T14:00:00Z"),
                        Instant.parse("2026-07-11T15:00:00Z"),
                        "America/New_York",
                        null)),
                "Asia/Shanghai");

        IcsCodec.IcsEvent result = codec.parse(content, "Asia/Shanghai").get(0);

        assertEquals(LocalDateTime.of(2026, 7, 11, 10, 0), result.startsAt());
        assertEquals(LocalDateTime.of(2026, 7, 11, 11, 0), result.endsAt());
        assertEquals("America/New_York", result.timezone());
    }

    @Test
    void keepsUtcLocalValuesWhenTheCustomTimezonePropertyIsAbsent() {
        String content = """
                BEGIN:VCALENDAR
                VERSION:2.0
                BEGIN:VEVENT
                UID:utc-event
                DTSTART:20260711T140000Z
                DTEND:20260711T150000Z
                SUMMARY:UTC event
                END:VEVENT
                END:VCALENDAR
                """;

        IcsCodec.IcsEvent result = codec.parse(content.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "Asia/Shanghai").get(0);

        assertEquals(LocalDateTime.of(2026, 7, 11, 14, 0), result.startsAt());
        assertEquals(LocalDateTime.of(2026, 7, 11, 15, 0), result.endsAt());
        assertEquals("Z", result.timezone());
    }
}
