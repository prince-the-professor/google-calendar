package org.upwork.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.upwork.dto.AppointmentRequest;
import org.upwork.dto.AppointmentSummary;
import org.upwork.dto.CancelRequest;
import org.upwork.service.CalendarService;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api")
public class AppointmentController {

    private final CalendarService calendarService;

    public AppointmentController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @PostMapping("/book-appointment")
    public ResponseEntity<String> bookAppointment(@RequestBody AppointmentRequest request) {
        String response = calendarService.handleBooking(request);
        if (response.startsWith("✅")) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @GetMapping("/appointments")
    public ResponseEntity<List<AppointmentSummary>> getAppointments() throws IOException {
        List<AppointmentSummary> upcoming = calendarService.getUpcomingAppointments();
        return ResponseEntity.ok(upcoming);
    }

    @PostMapping("/cancel-appointment")
    public ResponseEntity<String> cancelAppointment(@RequestBody CancelRequest request) {
        String response = calendarService.cancelAppointment(request);
        if (response.startsWith("✅")) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
}
