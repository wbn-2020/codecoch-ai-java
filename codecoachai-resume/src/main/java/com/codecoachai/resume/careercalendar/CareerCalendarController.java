package com.codecoachai.resume.careercalendar;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.resume.careercalendar.CareerCalendarModels.EventSave;
import com.codecoachai.resume.careercalendar.CareerCalendarModels.EventView;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/career-calendar")
public class CareerCalendarController {

    private final CareerCalendarService careerCalendarService;

    @GetMapping("/events")
    public Result<List<EventView>> list(@RequestParam(required = false) Instant from,
                                        @RequestParam(required = false) Instant to) {
        SecurityAssert.requireLoginUserId();
        return Result.success(careerCalendarService.list(from, to));
    }

    @OperationLog(module = "career-calendar", action = "CREATE_EVENT",
            description = "Create CRM calendar event", logResponse = false)
    @PostMapping("/events")
    public Result<EventView> create(@Valid @RequestBody EventSave request) {
        SecurityAssert.requireLoginUserId();
        return Result.success(careerCalendarService.create(request));
    }

    @OperationLog(module = "career-calendar", action = "UPDATE_EVENT",
            description = "Update CRM calendar event", logResponse = false)
    @PutMapping("/events/{eventId}")
    public Result<EventView> update(@PathVariable Long eventId, @Valid @RequestBody EventSave request) {
        SecurityAssert.requireLoginUserId();
        return Result.success(careerCalendarService.update(eventId, request));
    }

    @OperationLog(module = "career-calendar", action = "DELETE_EVENT",
            description = "Delete CRM calendar event", logResponse = false)
    @DeleteMapping("/events/{eventId}")
    public Result<Void> delete(@PathVariable Long eventId) {
        SecurityAssert.requireLoginUserId();
        careerCalendarService.delete(eventId);
        return Result.success();
    }

    @GetMapping("/export.csv")
    public void exportCsv(HttpServletResponse response,
                          @RequestParam(required = false) Instant from,
                          @RequestParam(required = false) Instant to) throws IOException {
        SecurityAssert.requireLoginUserId();
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=career-calendar.csv");
        response.getOutputStream().write(careerCalendarService.exportCsv(from, to));
    }

    @GetMapping("/export.ics")
    public void exportIcs(HttpServletResponse response,
                          @RequestParam(required = false) Instant from,
                          @RequestParam(required = false) Instant to,
                          @RequestParam String timezone) throws IOException {
        SecurityAssert.requireLoginUserId();
        response.setContentType("text/calendar;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=career-calendar.ics");
        response.getOutputStream().write(careerCalendarService.exportIcs(from, to, timezone));
    }
}
