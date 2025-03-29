package org.upwork;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Properties;

public class EmailSender {

    public static void sendEmail(Gmail service, String[] to, String subject, String bodyText) throws Exception {
        MimeMessage email = createEmail(to, "me", subject, bodyText);
        Message message = createMessageWithEmail(email);
        service.users().messages().send("me", message).execute();
    }

    private static MimeMessage createEmail(String[] to, String from, String subject, String bodyText)
            throws MessagingException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage email = new MimeMessage(session);
        email.setFrom(new InternetAddress(from));

        InternetAddress[] addresses = new InternetAddress[to.length];
        for (int i = 0; i < to.length; i++) {
            addresses[i] = new InternetAddress(to[i]);
        }

        email.addRecipients(javax.mail.Message.RecipientType.TO, addresses);
        email.setSubject(subject);
        email.setText(bodyText);
        return email;
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
}