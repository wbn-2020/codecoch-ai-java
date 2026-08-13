package com.codecoachai.resume.careerinterview.service;

import com.codecoachai.resume.careercalendar.entity.CareerCalendarEvent;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class CareerInterviewPreparationReaderImpl implements CareerInterviewPreparationReader {
    @Override
    public PreparationSnapshot read(CareerCalendarEvent event) {
        String status = event == null || event.getPreparationStatus() == null
                ? null : event.getPreparationStatus().trim().toUpperCase(Locale.ROOT);
        return new PreparationSnapshot(status, event == null ? null : event.getPreparationSourceHash(),
                "STALE".equals(status) || "GENERATING".equals(status));
    }
}
