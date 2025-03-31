package org.upwork.service;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import org.springframework.stereotype.Service;
import org.upwork.config.GoogleServiceHelper;

import javax.activation.DataHandler;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Properties;
import java.util.UUID;

@Service
public class EmailSenderService {

    private final Gmail gmailService;

    public EmailSenderService() throws Exception {
        gmailService = GoogleServiceHelper.getGmailService();
    }


    public void sendEmailWithCalendarInvite(
            String toEmail, String subject, String body, ZonedDateTime startTime, ZonedDateTime endTime
    ) throws Exception {

        MimeMessage email = createEmailWithICS(toEmail, "me", subject, body, startTime, endTime);
        Message message = createMessageWithEmail(email);
        gmailService.users().messages().send("me", message).execute();
    }

    private MimeMessage createEmailWithICS(
            String to, String from, String subject, String bodyText,
            ZonedDateTime start, ZonedDateTime end
    ) throws MessagingException, IOException {

        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);
        MimeMessage email = new MimeMessage(session);

        email.setFrom(new InternetAddress(from));
        email.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(to));
        email.setSubject(subject);

        // Text body part
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(bodyText);

        // Calendar attachment
        MimeBodyPart calendarPart = new MimeBodyPart();
        calendarPart.setHeader("Content-Class", "urn:content-classes:calendarmessage");
        calendarPart.setHeader("Content-ID", "calendar_message");
        calendarPart.setDataHandler(
                new DataHandler(new ByteArrayDataSource(buildBookingICS(start, end, to, subject), "text/calendar;method=REQUEST"))
        );

        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(textPart);
        multipart.addBodyPart(calendarPart);

        email.setContent(multipart);
        return email;
    }

    private String buildBookingICS(ZonedDateTime start, ZonedDateTime end, String email, String subject) {
        String dtStart = start.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        String dtEnd = end.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        String now = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));

        return "BEGIN:VCALENDAR\n" +
                "METHOD:REQUEST\n" +
                "PRODID:Google Calendar\n" +
                "VERSION:2.0\n" +
                "BEGIN:VEVENT\n" +
                "DTSTART:" + dtStart + "\n" +
                "DTEND:" + dtEnd + "\n" +
                "DTSTAMP:" + now + "\n" +
                "ORGANIZER;CN=AutoScheduler:mailto:me@example.com\n" +
                "UID:" + UUID.randomUUID() + "\n" +
                "ATTENDEE;ROLE=REQ-PARTICIPANT;RSVP=TRUE;CN=" + email + ":mailto:" + email + "\n" +
                "DESCRIPTION:Appointment Scheduled\n" +
                "SUMMARY:" + subject + "\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
    }

    private static Message createMessageWithEmail(MimeMessage email) throws MessagingException, IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        email.writeTo(buffer);
        byte[] rawMessageBytes = buffer.toByteArray();
        String encodedEmail = Base64.getUrlEncoder().encodeToString(rawMessageBytes);
        Message message = new Message();
        message.setRaw(encodedEmail);
        return message;
    }

    public void sendCancellationEmail(
            String toEmail, String subject, String body, ZonedDateTime start, ZonedDateTime end
    ) throws Exception {
        MimeMessage email = createCancelEmail(toEmail, "me", subject, body, start, end);
        Message message = createMessageWithEmail(email);
        gmailService.users().messages().send("me", message).execute();
    }

    private MimeMessage createCancelEmail(
            String to, String from, String subject, String bodyText,
            ZonedDateTime start, ZonedDateTime end
    ) throws MessagingException, IOException {

        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);
        MimeMessage email = new MimeMessage(session);

        email.setFrom(new InternetAddress(from));
        email.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(to));
        email.setSubject(subject);

        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(bodyText);

        MimeBodyPart calendarPart = new MimeBodyPart();
        calendarPart.setDataHandler(
                new DataHandler(new ByteArrayDataSource(
                        buildCancellationICS(start, end, to, subject, true),
                        "text/calendar;method=CANCEL"))
        );

        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(textPart);
        multipart.addBodyPart(calendarPart);

        email.setContent(multipart);
        return email;
    }

    private String buildCancellationICS(ZonedDateTime start, ZonedDateTime end, String email, String subject, boolean cancel) {
        String dtStart = start.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        String dtEnd = end.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        String now = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        String uid = UUID.randomUUID().toString();

        return "BEGIN:VCALENDAR\n" +
                "METHOD:" + (cancel ? "CANCEL" : "REQUEST") + "\n" +
                "PRODID:AutoScheduler\n" +
                "VERSION:2.0\n" +
                "BEGIN:VEVENT\n" +
                "UID:" + uid + "\n" +
                "DTSTAMP:" + now + "\n" +
                "DTSTART:" + dtStart + "\n";
    }
}