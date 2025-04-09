package org.upwork.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.upwork.config.GoogleServiceHelper;
import org.upwork.constants.GoogleOAuthConstants;
import org.upwork.dto.AppointmentRequest;
import org.upwork.dto.AppointmentSummary;
import org.upwork.dto.CancelRequest;
import org.upwork.entity.AppointmentAudit;
import org.upwork.entity.DoctorCredential;
import org.upwork.repository.AppointmentAuditRepository;
import org.upwork.repository.DoctorCredentialRepository;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

//@Slf4j
@Service
public class CalendarService {

    private static final Logger log = LoggerFactory.getLogger(CalendarService.class);
    private final EmailSenderService emailSenderService;
    private final DoctorCredentialRepository credentialRepository;
    private final AppointmentAuditRepository auditRepository;
    private final Calendar calendarService;

    @Autowired
    public CalendarService(EmailSenderService gmailSender,
                           DoctorCredentialRepository credentialRepository,
                           AppointmentAuditRepository auditRepository) throws Exception {
        this.emailSenderService = gmailSender;
        this.credentialRepository = credentialRepository;
        this.auditRepository = auditRepository;
        this.calendarService = GoogleServiceHelper.getCalendarService();
    }


    private Calendar getDoctorCalendarClient(String doctorEmail) throws Exception {
        DoctorCredential credential = credentialRepository.findByEmail(doctorEmail);
        if (credential == null) {
            throw new IllegalArgumentException("Doctor not registered or calendar access missing.");
        }

        GoogleCredential googleCredential = new GoogleCredential.Builder()
                .setTransport(GoogleNetHttpTransport.newTrustedTransport())
                .setJsonFactory(JacksonFactory.getDefaultInstance())
                .setClientSecrets(GoogleOAuthConstants.CLIENT_ID, GoogleOAuthConstants.CLIENT_SECRET)
                .build()
                .setAccessToken(credential.getAccessToken())
                .setRefreshToken(credential.getRefreshToken());

        if (googleCredential.getExpiresInSeconds() != null && googleCredential.getExpiresInSeconds() <= 60) {
            boolean refreshed = googleCredential.refreshToken();
            if (refreshed) {
                log.info("Token refreshed successfully for doctor: {}", credential.getDoctorId());
                credential.setAccessToken(googleCredential.getAccessToken());
                credential.setTokenExpiry(googleCredential.getExpiresInSeconds());
                credentialRepository.save(credential);
            } else {
                log.warn("Failed to refresh token for doctor : {}", credential.getDoctorId());
            }
        }

        return new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JacksonFactory.getDefaultInstance(),
                googleCredential
        ).setApplicationName("Doctor-schedular-web").build();
    }

    public String bookDoctorAppointment(AppointmentRequest request) {
        try {
            ZonedDateTime requestedStart = ZonedDateTime.parse(request.getStartTime());
            ZonedDateTime requestedEnd = requestedStart.plusHours(1);
            if (!isValidSlot(requestedStart)) {
                return "❌ Slot must be between 9 AM to 5 PM, Monday to Friday.";
            }

            Calendar calendar = getDoctorCalendarClient(request.getDoctorEmail());

            if (isSlotAvailable(calendar, requestedStart, requestedEnd)) {
                Event event = new Event()
                        .setSummary("Appointment with " + request.getFirstName() + " " + request.getLastName())
                        .setDescription("Auto-scheduled for " + request.getPatientEmail())
                        .setStart(new EventDateTime().setDateTime(new DateTime(requestedStart.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))).setTimeZone("Asia/Kolkata"))
                        .setEnd(new EventDateTime().setDateTime(new DateTime(requestedEnd.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))).setTimeZone("Asia/Kolkata"));

                Event created = calendar.events().insert("primary", event).execute();

                emailSenderService.sendEmailWithCalendarInvite(
                        request.getPatientEmail(),
                        "Appointment Confirmation",
                        "Your appointment with " + request.getDoctorEmail() + " has been scheduled.",
                        requestedStart,
                        requestedEnd,
                        "princenoida142@gmail.com"
                );

                AppointmentAudit audit = new AppointmentAudit();
                audit.setDoctorEmail(request.getDoctorEmail());
                audit.setPatientEmail(request.getPatientEmail());
                audit.setFirstName(request.getFirstName());
                audit.setLastName(request.getLastName());
                audit.setSlotStart(requestedStart);
                audit.setSlotEnd(requestedEnd);
                audit.setEventId(created.getId());
                audit.setStatus("BOOKED");
                audit.setCreatedAt(ZonedDateTime.now());
                auditRepository.save(audit);

                return "✅ Appointment booked successfully. Event ID: " + created.getId();
            } else {
                ZonedDateTime nextAvailable = findNextAvailableSlot(calendar, requestedStart);
                if (nextAvailable != null) {
                    return "❌ Slot unavailable. Next available: " + nextAvailable;
                } else {
                    return "❌ No available slots within the next week.";
                }
//                return "❌ Slot not available. Please choose another time.";
            }
        } catch (Exception e) {
            log.error("Error while booking appointment: {}", e.getMessage(), e);
            return "❌ Error while booking appointment: " + e.getMessage();
        }
    }

    private boolean isSlotAvailable(Calendar calendar, ZonedDateTime start, ZonedDateTime end) throws IOException {
        DateTime timeMin = new DateTime(start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        DateTime timeMax = new DateTime(end.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

        FreeBusyRequest request = new FreeBusyRequest()
                .setTimeMin(timeMin)
                .setTimeMax(timeMax)
                .setItems(List.of(new FreeBusyRequestItem().setId("primary")));

        FreeBusyResponse response = calendar.freebusy().query(request).execute();
        return response.getCalendars().get("primary").getBusy().isEmpty();
    }

    private boolean isValidSlot(ZonedDateTime start) {
        return !start.getDayOfWeek().equals(DayOfWeek.SATURDAY)
                && !start.getDayOfWeek().equals(DayOfWeek.SUNDAY)
                && start.getHour() >= 9 && start.getHour() <= 16;
    }

//    private boolean isValidSlot(ZonedDateTime start) {
//        return !start.getDayOfWeek().equals(DayOfWeek.SATURDAY)
//                && !start.getDayOfWeek().equals(DayOfWeek.SUNDAY)
//                && start.getHour() >= 9 && start.getHour() <= 16;
//    }
//
//    private boolean isSlotAvailable(ZonedDateTime start, ZonedDateTime end) throws IOException {
//        DateTime timeMin = new DateTime(start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
//        DateTime timeMax = new DateTime(end.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
//
//        FreeBusyRequest request = new FreeBusyRequest()
//                .setTimeMin(timeMin)
//                .setTimeMax(timeMax)
//                .setItems(Collections.singletonList(new FreeBusyRequestItem().setId("primary")));
//
//        FreeBusyResponse response = calendarService.freebusy().query(request).execute();
//        return response.getCalendars().get("primary").getBusy().isEmpty();
//    }


//    private void createEvent(AppointmentRequest req, ZonedDateTime start, ZonedDateTime end) throws Exception {
//        Event event = new Event()
//                .setSummary("Appointment with " + req.getFirstName() + " " + req.getLastName())
//                .setDescription("Auto-scheduled for " + req.getPatientEmail())
//                .setStart(new EventDateTime().setDateTime(new DateTime(start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))).setTimeZone("Asia/Kolkata"))
//                .setEnd(new EventDateTime().setDateTime(new DateTime(end.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))).setTimeZone("Asia/Kolkata"));
//
//        System.out.println("EventId : " + event.getId());
//        calendarService.events().insert("primary", event).execute();
//        emailSenderService.sendEmailWithCalendarInvite(req.getPatientEmail(), "Appointment Confirmation",
//                "Hi " + req.getFirstName() + ", \n\n Your appointment has been scheduled at "
//                        + start, start, end);
//    }

    private ZonedDateTime findNextAvailableSlot(Calendar calendar, ZonedDateTime from) throws IOException {
        ZonedDateTime slot = from;
        ZonedDateTime oneWeekLater = from.plusDays(7);

        while (slot.isBefore(oneWeekLater)) {
            if (isValidSlot(slot)) {
                ZonedDateTime end = slot.plusHours(1);
                if (isSlotAvailable(calendar, slot, end)) {
                    return slot;
                }
            }
            slot = slot.plusHours(1);
        }
        return null;
    }

    public List<AppointmentSummary> getUpcomingAppointments(String doctorEmailId) throws Exception {
        Calendar calendar = getDoctorCalendarClient(doctorEmailId);

        List<AppointmentSummary> summaryList = new ArrayList<>();

        try {
            DateTime now = new DateTime(System.currentTimeMillis());
            DateTime oneWeekLater = new DateTime(System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000));

            Events events = calendar.events().list("primary")
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
                    summary.setPatientEmailId(desc.replace("Auto-scheduled for ", "").trim());
                }
                summaryList.add(summary);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return summaryList;
    }

    public String cancelAppointment(CancelRequest request) throws Exception {
        if (request.getEventId() == null || request.getEventId().isBlank()) {
            return "❌ Event ID is required to cancel an appointment.";
        }
        Calendar calendar = getDoctorCalendarClient(request.getDoctorEmailId());
        try {
            // Retrieve the event details
            Event event = calendar.events().get("primary", request.getEventId()).execute();
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
            calendar.events().delete("primary", request.getEventId()).execute();

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

    public String registerDoctor(String authCode, String doctorId) {
        try {
            GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    JacksonFactory.getDefaultInstance(),
                    "https://oauth2.googleapis.com/token",
                    GoogleOAuthConstants.CLIENT_ID,
                    GoogleOAuthConstants.CLIENT_SECRET,
                    authCode,
                    "http://localhost:8080/auth/google/callback"
            ).execute();

            GoogleCredential credential = new GoogleCredential.Builder()
                    .setTransport(GoogleNetHttpTransport.newTrustedTransport())
                    .setJsonFactory(JacksonFactory.getDefaultInstance())
                    .setClientSecrets(GoogleOAuthConstants.CLIENT_ID, GoogleOAuthConstants.CLIENT_SECRET)
                    .build()
                    .setFromTokenResponse(tokenResponse);

            Calendar calendarService = new Calendar.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    JacksonFactory.getDefaultInstance(),
                    credential
            ).setApplicationName("Doctor-schedular-web").build();

            String email = calendarService.calendarList().list().execute()
                    .getItems().stream().findFirst().get().getId();

            DoctorCredential doc = new DoctorCredential();
            doc.setDoctorId(doctorId);
            doc.setEmail(email);
            doc.setAccessToken(tokenResponse.getAccessToken());
            doc.setRefreshToken(tokenResponse.getRefreshToken());
            doc.setTokenExpiry(tokenResponse.getExpiresInSeconds());
            credentialRepository.save(doc);
            System.out.println(doc.toString());

            log.info("Doctor {} successfully registered with email {}", doctorId, email);
            return "✅ Doctor registered successfully";

        } catch (Exception e) {
            log.error("Error registering doctor {}: {}", doctorId, e.getMessage(), e);
            return "❌ Failed to register doctor: " + e.getMessage();
        }
    }

    // Additional logic for scheduling and availability checking will go here

}