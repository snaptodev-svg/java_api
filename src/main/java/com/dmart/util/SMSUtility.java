package com.dmart.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SMSUtility {
    private static final Logger LOGGER = Logger.getLogger(SMSUtility.class.getName());
    
    private static final String MSG91_USER_NAME_TRANSACTIONAL_SMS = System.getenv("MSG91_AUTH_KEY");
    private static final String MSG91_ROUTE_TRANSACTIONAL_SMS = System.getenv("MSG91_ROUTE");
    private static final String MSG91SMS_API = System.getenv("MSG91_API_URL");
    private static final String MSG91_SENDER_ID = System.getenv("MSG91_SENDER_ID");

    public static boolean SendTransactionalSMSMSG91ROCKET(String mobileNumber, String text, String dleId) {
        try {
            // Validate inputs
            if (!validateInputs(mobileNumber, text, dleId)) {
                LOGGER.log(Level.SEVERE, "Invalid input parameters");
                return false;
            }

            // Validate environment variables
            if (MSG91_USER_NAME_TRANSACTIONAL_SMS == null || MSG91_USER_NAME_TRANSACTIONAL_SMS.isEmpty()) {
                LOGGER.log(Level.SEVERE, "MSG91_AUTH_KEY environment variable not set");
                return false;
            }
            if (MSG91SMS_API == null || MSG91SMS_API.isEmpty()) {
                LOGGER.log(Level.SEVERE, "MSG91_API_URL environment variable not set");
                return false;
            }

            // Encoding message
            String encoded_message = URLEncoder.encode(text, "UTF-8");
            
            // Prepare parameter string
            StringBuilder sbPostData = new StringBuilder(MSG91SMS_API);
            sbPostData.append("authkey=").append(MSG91_USER_NAME_TRANSACTIONAL_SMS);
            sbPostData.append("&mobiles=").append("+91").append(mobileNumber);
            sbPostData.append("&message=").append(encoded_message);
            sbPostData.append("&route=").append(MSG91_ROUTE_TRANSACTIONAL_SMS);
            sbPostData.append("&sender=").append(MSG91_SENDER_ID);
            sbPostData.append("&DLT_TE_ID=").append(dleId);

            String requestUrl = sbPostData.toString();
            LOGGER.log(Level.INFO, "Sending SMS to: +91" + mobileNumber);

            URL url = new URL(requestUrl);
            HttpURLConnection uc = (HttpURLConnection) url.openConnection();
            uc.setConnectTimeout(5000);
            uc.setReadTimeout(5000);
            uc.connect();

            // Check HTTP response code
            int responseCode = uc.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                LOGGER.log(Level.SEVERE, "HTTP Error: " + responseCode);
                return false;
            }

            // Read and validate response
            String response = readResponse(uc);
            LOGGER.log(Level.INFO, "API Response: " + response);

            // MSG91 returns "0" for success
            if (response != null && response.trim().equals("0")) {
                LOGGER.log(Level.INFO, "SMS sent successfully to: +91" + mobileNumber);
                return true;
            } else {
                LOGGER.log(Level.SEVERE, "SMS sending failed. API Response: " + response);
                return false;
            }

        } catch (MalformedURLException ex) {
            LOGGER.log(Level.SEVERE, "Invalid URL: " + ex.getMessage(), ex);
            return false;
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "IO Exception while sending SMS: " + ex.getMessage(), ex);
            return false;
        }
    }

    private static String readResponse(HttpURLConnection uc) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(uc.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }

    private static boolean validateInputs(String mobileNumber, String text, String dleId) {
        if (mobileNumber == null || mobileNumber.isEmpty()) {
            LOGGER.log(Level.SEVERE, "Mobile number is empty");
            return false;
        }
        if (text == null || text.isEmpty()) {
            LOGGER.log(Level.SEVERE, "Message text is empty");
            return false;
        }
        if (dleId == null || dleId.isEmpty()) {
            LOGGER.log(Level.SEVERE, "DLT_TE_ID is empty");
            return false;
        }
        // Allow 10 digit or number with country code
        if (mobileNumber.length() < 10 || mobileNumber.length() > 15) {
            LOGGER.log(Level.SEVERE, "Invalid mobile number length: " + mobileNumber.length());
            return false;
        }
        return true;
    }
}