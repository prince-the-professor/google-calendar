package org.upwork;

import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.gmail.Gmail;

import java.time.ZonedDateTime;

public class MainApp {
    public static void main(String[] args) {
        try {
            Calendar calendarService = GoogleServiceHelper.getCalendarService();
            Gmail gmailService = GoogleServiceHelper.getGmailService();

            String calendarId = "primary";
//            DateTime slot = CalendarScheduler.getNextAvailableSlot(calendarService, calendarId);

            String scheduleTime = "2025-03-28T13:00:00+05:30";
            ZonedDateTime start = ZonedDateTime.parse(scheduleTime);
            ZonedDateTime end = start.plusHours(1);

            boolean flag = CalendarScheduler.isSlotAvailable
                    (calendarService, calendarId, start, end);

            System.out.println("Appointment scheduled for : " + start);
            if (flag) {
                Event appointment = CalendarScheduler.createAppointment(calendarService, calendarId, start, end);
                EmailSender.sendEmail(gmailService,
                        new String[] {"advanceprince@gmail.com", "sekarzh20@gmail.com"},
                        "Your Appointment is Confirmed",
                        "Scheduled for: " + start);
                System.out.println("✅ Appointment created and confirmation sent.");
            } else {
                System.out.println("❌ No available slot found.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}