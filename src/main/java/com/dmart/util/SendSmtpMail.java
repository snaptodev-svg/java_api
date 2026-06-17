package com.dmart.util;

import com.sun.net.ssl.internal.ssl.Provider;
import java.io.UnsupportedEncodingException;
import java.security.Security;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class SendSmtpMail {

    private static Session session;

    public static String sendSSLMessage(String[] recipients, String subject, String message)
            throws MessagingException, UnsupportedEncodingException {

        String from = "info@snapto.in";
        String sendStatus = null;
        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.gmail.com"); //SMTP Host
        props.put("mail.smtp.starttls.enable", "true"); //SSL Port
        props.put("mail.smtp.auth", "true"); //Enabling SMTP Authentication
        props.put("mail.smtp.port", 587); //SMTP Port

        session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("info@snapto.in", "oyjcxzwfqrrlxnfn");
            }
        });

        Message msg = new MimeMessage(session);
        InternetAddress addressFrom = new InternetAddress(from, "Snapto");
        msg.setFrom(addressFrom);
        InternetAddress[] addressTo = new InternetAddress[recipients.length];
        for (int i = 0; i < recipients.length; i++) {
            addressTo[i] = new InternetAddress(recipients[i]);
        }
        msg.setRecipients(Message.RecipientType.TO, addressTo);

        msg.setSubject(subject);

        msg.setContent(message, "text/html");
        new Thread(() -> {
            try {
                Transport.send(msg);
            } catch (MessagingException ex) {
                Logger.getLogger(SendSmtpMail.class.getName()).log(Level.SEVERE, null, ex);
            }
            Security.addProvider(new Provider());
        }).start();
        sendStatus = "MailSuccesfullysend";
        return sendStatus;
    }

    public static String sendSSLMessagewithBcc(String[] recipients, String[] ccrecipients, String subject, String message)
            throws MessagingException, UnsupportedEncodingException {

        // String from = "info@snapto.in";
        String from = "mohitdevdubey@gmail.com";
        String sendStatus = null;
        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.gmail.com"); //SMTP Host
        props.put("mail.smtp.starttls.enable", "true"); //SSL Port
        props.put("mail.smtp.auth", "true"); //Enabling SMTP Authentication
        props.put("mail.smtp.port", 587); //SMTP Port

        session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                // return new PasswordAuthentication("info@snapto.in", "oyjcxzwfqrrlxnfn");
         return new PasswordAuthentication("mohitdevdubey@gmail.com", "gkzlextlbhkoqpss");
        
            }
        });

        Message msg = new MimeMessage(session);
        InternetAddress addressFrom = new InternetAddress(from, "Snapto");
        msg.setFrom(addressFrom);
        InternetAddress[] addressCC = new InternetAddress[ccrecipients.length];
        for (int i = 0; i < recipients.length; i++) {
            addressCC[i] = new InternetAddress(ccrecipients[i]);
        }
        msg.setRecipients(Message.RecipientType.BCC, addressCC);
        InternetAddress[] addressTo = new InternetAddress[recipients.length];
        for (int i = 0; i < recipients.length; i++) {
            addressTo[i] = new InternetAddress(recipients[i]);
        }
        msg.setRecipients(Message.RecipientType.TO, addressTo);

        msg.setSubject(subject);

        msg.setContent(message, "text/html");
        new Thread(() -> {
            try {
                Transport.send(msg);
            } catch (MessagingException ex) {
                Logger.getLogger(SendSmtpMail.class.getName()).log(Level.SEVERE, null, ex);
            }
            Security.addProvider(new Provider());
        }).start();
        sendStatus = "MailSuccesfullysend";
        return sendStatus;
    }
}
