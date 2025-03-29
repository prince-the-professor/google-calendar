package org.upwork;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;

import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import static java.time.OffsetDateTime.*;

public class CalendarScheduler {

    public static boolean isSlotAvailable(Calendar calendarService, String calendarId, ZonedDateTime start, ZonedDateTime end) throws IOException {
        DateTime timeMin = new DateTime(start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        DateTime timeMax = new DateTime(end.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

        FreeBusyRequest request = new FreeBusyRequest()
                .setTimeMin(timeMin)
                .setTimeMax(timeMax)
                .setItems(Collections.singletonList(new FreeBusyRequestItem().setId(calendarId)));

        FreeBusyResponse response = calendarService.freebusy().query(request).execute();
        List<TimePeriod> busyTimes = response.getCalendars().get(calendarId).getBusy();

        return busyTimes.isEmpty(); // ✅ If empty, slot is available
    }

    public static DateTime getNextAvailableSlot(Calendar calendarService, String calendarId) throws IOException {
//        DateTime now = new DateTime(System.currentTimeMillis());
//        DateTime oneWeekLater = new DateTime(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000);

        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime oneWeekLaterZdt = now.plusDays(7);

        DateTime min = new DateTime(now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        DateTime max = new DateTime(oneWeekLaterZdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

        Calendar.Events.List request = calendarService.events().list(calendarId)
                .setTimeMin(min)
                .setTimeMax(max)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .setTimeZone("Asia/Kolkata");
        Events events = request.execute();

        for (Event event : events.getItems()) {
            System.out.println(event.getSummary() +",  start-time : "+ event.getStart().getDateTime() + ", end-time: " + event.getEnd().getDateTime());
        }



        /*Events events = calendarService.events().list(calendarId)
                .setTimeMin(min)
                .setTimeMax(max)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .setTimeZone("Asia/Kolkata")
                .execute();

        System.out.println("Events count : " + events.getItems().size());
        List<Event> items = events.getItems();

        // Assuming working hours 9am to 5pm
//        LocalDateTime slot = LocalDateTime.now().withHour(9).withMinute(0);

        ZonedDateTime slot = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).plusDays(1).withHour(9).withMinute(0);

        // Convert to LocalDateTime (IST time, without the zone info)
//        LocalDateTime slot = istTime.toLocalDateTime().withHour(9).withMinute(0);
        while (slot.getHour() < 17) {
            boolean occupied = false;

            for (Event e : items) {
                DateTime start = e.getStart().getDateTime();
                DateTime end = e.getEnd().getDateTime();
                if (start == null || end == null) continue;

//                LocalDateTime s = LocalDateTime.parse(OffsetDateTime.parse(start,"yyyy-MM-dd'T'HH:mm:ss.SSS XXX" ));
//                LocalDateTime eTime = LocalDateTime.parse(end.toStringRfc3339());

                String startString = start.toStringRfc3339();  // Get the string representation of the DateTime
                String endString = end.toStringRfc3339();

                DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME; // RFC 3339 format, standard

// Parse the string into OffsetDateTime (including timezone)
                OffsetDateTime s = OffsetDateTime.parse(startString, formatter);
                OffsetDateTime eTime = OffsetDateTime.parse(endString, formatter);

// You can work with the OffsetDateTime objects here, or if needed, convert to LocalDateTime (ignoring timezone)
                LocalDateTime localStart = s.toLocalDateTime();
                LocalDateTime localEnd = eTime.toLocalDateTime();

                if (!slot.isBefore(localStart.atZone(ZoneId.of("Asia/Kolkata"))) && slot.isBefore(localEnd.atZone(ZoneId.of("Asia/Kolkata")))) {
                    occupied = true;
                    break;
                }
            }

            if (!occupied) {

                // Convert the UTC ZonedDateTime to a string in RFC3339 format
                String rfc3339String = slot.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

                // Now create a Google API DateTime object from the RFC3339 string
                return new DateTime(rfc3339String);
            }

            slot = slot.plusHours(1);
        }
*/
        return null;
    }

    public static Event createAppointment(Calendar service, String calendarId, ZonedDateTime startTime, ZonedDateTime endTime) throws IOException {
        Event event = new Event()
                .setSummary("Auto Scheduled Slot")
                .setDescription("Scheduled via Java App");

        EventDateTime start = new EventDateTime()
                .setDateTime(new DateTime(startTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)))
                .setTimeZone("Asia/Kolkata");
        EventDateTime end = new EventDateTime()
                .setDateTime(new DateTime(endTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)))
                .setTimeZone("Asia/Kolkata");

        event.setStart(start).setEnd(end);

        return service.events().insert(calendarId, event).execute();
    }
}