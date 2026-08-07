package com.codecoachai.resume.careerinterview.service;

import com.codecoachai.resume.careercalendar.entity.CareerCalendarEvent;

public interface CareerInterviewPreparationReader {
    PreparationSnapshot read(CareerCalendarEvent event);

    record PreparationSnapshot(String status, String sourceHash, boolean stale) {
        public boolean usable() {
            return !stale && sourceHash != null && !sourceHash.isBlank()
                    && ("READY".equals(status) || "FALLBACK".equals(status));
        }
    }
}
