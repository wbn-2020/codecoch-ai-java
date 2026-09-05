package com.codecoachai.resume.careercalendar;

import com.codecoachai.resume.careercalendar.CareerCalendarModels.EventSave;
import com.codecoachai.resume.careercalendar.CareerCalendarModels.EventView;
import com.codecoachai.resume.careercalendar.CareerCalendarModels.ImportedEvent;
import java.time.Instant;
import java.util.List;

public interface CareerCalendarService {

    EventView create(EventSave request);

    EventView update(Long eventId, EventSave request);

    void delete(Long eventId);

    List<EventView> list(Instant from, Instant to);

    byte[] exportCsv(Instant from, Instant to);

    byte[] exportIcs(Instant from, Instant to, String calendarTimezone);

    EventView createImported(Long userId, ImportedEvent event);
}
