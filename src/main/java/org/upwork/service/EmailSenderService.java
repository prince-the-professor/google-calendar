package org.upwork.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.upwork.config.GoogleServiceHelper;
import org.upwork.entity.DoctorCredential;
import org.upwork.repository.DoctorCredentialRepository;

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

    @Value("${google.client.id}")
    private String clientId;

    @Value("${google.client.secret}")
    private String clientSecret;

    private final DoctorCredentialRepository credentialRepository;
    private final Gmail gmailService;

    @Autowired
    public EmailSenderService(DoctorCredentialRepository credentialRepository) throws Exception {
        this.credentialRepository = credentialRepository;
        gmailService = GoogleServiceHelper.getGmailService();
    }

    public void sendEmailWithCalendarInvite(String toEmail, String subject, String body,
                                            ZonedDateTime startTime, ZonedDateTime endTime,
                                            String doctorEmail) throws Exception {

        // 1. Fetch and refresh doctor token
//        DoctorCredential credential = credentialRepository.findByEmail(doctorEmail);
//        if (credential == null) throw new IllegalArgumentException("Doctor not registered");
//
//        GoogleCredential googleCredential = new GoogleCredential.Builder()
//                .setTransport(GoogleNetHttpTransport.newTrustedTransport())
//                .setJsonFactory(JacksonFactory.getDefaultInstance())
//                .setClientSecrets(clientId, clientSecret)
//                .build()
//                .setAccessToken(credential.getAccessToken())
//                .setRefreshToken(credential.getRefreshToken());
//
//        if (googleCredential.getExpiresInSeconds() != null && googleCredential.getExpiresInSeconds() <= 60) {
//            boolean refreshed = googleCredential.refreshToken();
//            if (!refreshed) throw new IllegalStateException("Token refresh failed");
//            credential.setAccessToken(googleCredential.getAccessToken());
//            credential.setTokenExpiry(googleCredential.getExpirationTimeMilliseconds());
//            credentialRepository.save(credential);
//        }
//
//        // 2. Build Gmail client
//        Gmail gmailService = new Gmail.Builder(
//                GoogleNetHttpTransport.newTrustedTransport(),
//                JacksonFactory.getDefaultInstance(),
//                googleCredential
//        ).setApplicationName("Doctor Scheduler").build();

        // 3. Create and send email
        MimeMessage email = createEmailWithICS(toEmail, doctorEmail, subject, body, startTime, endTime);
        Message message = createMessageWithEmail(email);
        gmailService.users().messages().send("me", message).execute(); // "me" works with valid token
    }

    private MimeMessage createEmailWithICS(String to, String from, String subject, String bodyText,
                                           ZonedDateTime start, ZonedDateTime end) throws MessagingException, IOException {

        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);
        MimeMessage email = new MimeMessage(session);

        email.setFrom(new InternetAddress(from));
        email.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(to));
        email.setSubject(subject);

        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(bodyText);

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

    private static Message createMessageWithEmail(MimeMessage email) throws IOException, MessagingException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        email.writeTo(buffer);
        String encodedEmail = Base64.getUrlEncoder().encodeToString(buffer.toByteArray());
        Message message = new Message();
        message.setRaw(encodedEmail);
        return message;
    }

    private String buildBookingICS(ZonedDateTime start, ZonedDateTime end, String email, String subject) {
        String dtStart = start.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        String dtEnd = end.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        String now = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));

        return "BEGIN:VCALENDAR\n" +
                "METHOD:REQUEST\n" +
                "PRODID:DoctorScheduler\n" +
                "VERSION:2.0\n" +
                "BEGIN:VEVENT\n" +
                "DTSTART:" + dtStart + "\n" +
                "DTEND:" + dtEnd + "\n" +
                "DTSTAMP:" + now + "\n" +
                "ORGANIZER;CN=DoctorScheduler:mailto:" + email + "\n" +
                "UID:" + UUID.randomUUID() + "\n" +
                "ATTENDEE;ROLE=REQ-PARTICIPANT;RSVP=TRUE;CN=" + email + ":mailto:" + email + "\n" +
                "DESCRIPTION:Appointment Scheduled\n" +
                "SUMMARY:" + subject + "\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";
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