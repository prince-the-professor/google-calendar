package org.upwork.service;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;
import org.springframework.stereotype.Service;
import org.upwork.config.GoogleServiceHelper;
import org.upwork.dto.AppointmentRequest;
import org.upwork.dto.AppointmentSummary;
import org.upwork.dto.CancelRequest;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class CalendarService {

    private final Calendar calendarService;
    private final EmailSenderService emailSenderService;

    public CalendarService(EmailSenderService emailSenderService) throws Exception {
        this.calendarService = GoogleServiceHelper.getCalendarService();
        this.emailSenderService = emailSenderService;
    }

    public String handleBooking(AppointmentRequest request) {
        try {
            ZonedDateTime requestedStart = ZonedDateTime.parse(request.getStartTime());

            // Ensure slot is within 9 AM - 5 PM and weekday
            if (!isValidSlot(requestedStart)) {
                return "❌ Slot must be between 9 AM to 5 PM, Monday to Friday.";
            }

            ZonedDateTime requestedEnd = requestedStart.plusHours(1);

            if (isSlotAvailable(requestedStart, requestedEnd)) {
                createEvent(request, requestedStart, requestedEnd);
                return "✅ Appointment booked successfully at " + requestedStart;
            } else {
                ZonedDateTime nextAvailable = findNextAvailableSlot(requestedStart);
                if (nextAvailable != null) {
                    return "❌ Slot unavailable. Next available: " + nextAvailable;
                } else {
                    return "❌ No available slots within the next week.";
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Error occurred: " + e.getMessage();
        }
    }

    private boolean isValidSlot(ZonedDateTime start) {
        return !start.getDayOfWeek().equals(DayOfWeek.SATURDAY)
                && !start.getDayOfWeek().equals(DayOfWeek.SUNDAY)
                && start.getHour() >= 9 && start.getHour() <= 16;
    }

    private boolean isSlotAvailable(ZonedDateTime start, ZonedDateTime end) throws IOException {
        DateTime timeMin = new DateTime(start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        DateTime timeMax = new DateTime(end.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

        FreeBusyRequest request = new FreeBusyRequest()
                .setTimeMin(timeMin)
                .setTimeMax(timeMax)
                .setItems(Collections.singletonList(new FreeBusyRequestItem().setId("primary")));

        FreeBusyResponse response = calendarService.freebusy().query(request).execute();
        return response.getCalendars().get("primary").getBusy().isEmpty();
    }

    private void createEvent(AppointmentRequest req, ZonedDateTime start, ZonedDateTime end) throws Exception {
        Event event = new Event()
                .setSummary("Appointment with " + req.getFirstName() + " " + req.getLastName())
                .setDescription("Auto-scheduled for " + req.getEmail())
                .setStart(new EventDateTime().setDateTime(new DateTime(start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))).setTimeZone("Asia/Kolkata"))
                .setEnd(new EventDateTime().setDateTime(new DateTime(end.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))).setTimeZone("Asia/Kolkata"));

        System.out.println("EventId : " + event.getId());
        calendarService.events().insert("primary", event).execute();
        emailSenderService.sendEmailWithCalendarInvite(req.getEmail(), "Appointment Confirmation",
                "Hi " + req.getFirstName() + ", \n\n Your appointment has been scheduled at "
                        + start, start, end);
    }

    private ZonedDateTime findNextAvailableSlot(ZonedDateTime from) throws IOException {
        ZonedDateTime slot = from;
        ZonedDateTime oneWeekLater = from.plusDays(7);

        while (slot.isBefore(oneWeekLater)) {
            if (isValidSlot(slot)) {
                ZonedDateTime end = slot.plusHours(1);
                if (isSlotAvailable(slot, end)) {
                    return slot;
                }
            }
            slot = slot.plusHours(1);
        }
        return null;
    }

    public List<AppointmentSummary> getUpcomingAppointments() throws IOException {
        List<AppointmentSummary> summaryList = new ArrayList<>();

        try {
            DateTime now = new DateTime(System.currentTimeMillis());
            DateTime oneWeekLater = new DateTime(System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000));

            Events events = calendarService.events().list("primary")
                    .setTimeMin(now)
                    .setTimeMax(oneWeekLater)
                    .setSingleEvents(true)
                    .setOrderBy("startTime")
                    .execute();
            for (Event event : events.getItems()) {
                AppointmentSummary summary = new AppointmentSummary();
                summary.setEventId(event.getId());
                summary.setStartTime(event.getStart().getDateTime().toString());
                summary.setEndTime(event.getEnd().getDateTime().toString());
                summary.setEventDescription(event.getSummary());

                String summaryDesc = event.getSummary();
                String desc = event.getDescription();

                if (summaryDesc != null && summaryDesc.startsWith("Appointment with ")) {
                    String namePart = summaryDesc.replace("Appointment with ", "");
                    String[] names = namePart.split(" ", 2);
                    if (names.length == 2) {
                        summary.setFirstName(names[0]);
                        summary.setLastName(names[1]);
                    }
                }
                if (desc != null && desc.contains("@")) {
                    summary.setEmailId(desc.replace("Auto-scheduled for ", "").trim());
                }
                summaryList.add(summary);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return summaryList;
    }

    public String cancelAppointment(CancelRequest request) {
        if (request.getEventId() == null || request.getEventId().isBlank()) {
            return "❌ Event ID is required to cancel an appointment.";
        }
        try {
            // Retrieve the event details
            Event event = calendarService.events().get("primary", request.getEventId()).execute();
            if (event == null) {
                return "❌ No appointment found with the provided event ID.";
            }

            // Parse event details
            String summary = event.getSummary();
            String description = event.getDescription();
            ZonedDateTime start = ZonedDateTime.parse(event.getStart().getDateTime().toString());
            ZonedDateTime end = ZonedDateTime.parse(event.getEnd().getDateTime().toString());

            String firstName = "";
            String lastName = "";
            String email = "";

            if (summary != null && summary.startsWith("Appointment with ")) {
                String[] names = summary.replace("Appointment with ", "").split(" ", 2);
                if (names.length == 2) {
                    firstName = names[0];
                    lastName = names[1];
                }
            }

            if (description != null && description.contains("@")) {
                email = description.replace("Auto-scheduled for ", "").trim();
            }

            // Delete the event from Google Calendar
            calendarService.events().delete("primary", request.getEventId()).execute();

            if (email.isBlank()) {
                return "✅ Appointment cancelled but patient email not found for notification.";
            }

            emailSenderService.sendCancellationEmail(
                    email,
                    "Appointment Cancelled",
                    "Dear " + firstName + ", your appointment scheduled for " + start + " has been cancelled.",
                    start,
                    end
            );

            return "✅ Appointment cancelled and notification sent to " + email;
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404) {
                return "❌ Appointment not found in calendar.";
            } else {
                e.printStackTrace();
                return "❌ Google Calendar API error: " + e.getDetails().getMessage();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Error while cancelling appointment: " + e.getMessage();
        }
    }
}