/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.dmart.util;

import com.dmart.model.Constants;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import javax.xml.bind.DatatypeConverter;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author Rahul
 */
public class CommonUtil {

    public static String calculateRFC2104HMAC(String data, String secret)
            throws java.security.SignatureException {
        String result;
        try {
            // get an hmac_sha256 key from the raw secret bytes
            SecretKeySpec signingKey = new SecretKeySpec(secret.getBytes(), Constants.HMAC_SHA256_ALGORITHM);
            // get an hmac_sha256 Mac instance and initialize with the signing key
            Mac mac = Mac.getInstance(Constants.HMAC_SHA256_ALGORITHM);
            mac.init(signingKey);
            // compute the hmac on input data bytes
            byte[] rawHmac = mac.doFinal(data.getBytes());
            // base64-encode the hmac
            result = DatatypeConverter.printHexBinary(rawHmac).toLowerCase();
        } catch (IllegalStateException | InvalidKeyException | NoSuchAlgorithmException e) {
            throw new SignatureException("Failed to generate HMAC : " + e.getMessage());
        }
        return result;
    }

    /**
     * @return Returns random OTP to be used for an agent
     */
    public static String generateOTP() {
        // Generate 6-Digit Key
        int length = 6;
        final String characters = "1836547290";
        StringBuilder result1 = new StringBuilder();
        while (length > 0) {
            Random rand = new Random();
            result1.append(characters.charAt(rand.nextInt(characters.length())));
            length--;
        }
        return result1.toString();
    }

    public static String userVerify(String token, boolean isProdDB) {
        String email = "";

        String resData = "{me{id,email}}";

        JSONObject obje = new JSONObject();
        obje.put("query", resData);

        ArrayList<BasicNameValuePair> headers = new ArrayList<>();
        headers.add(0, new BasicNameValuePair("Content-Type",
                "application/json"));
        headers.add(0, new BasicNameValuePair("Authorization",
                "JWT " + token));

        String url;
        if (isProdDB) {
            url = Constants.HEGMA_API_URL;
        } else {
            url = Constants.HEGMA_BETA_API_URL;
        }
        String apiResponse = null;
        try {
            apiResponse = HTTPUtil.executeUrl(new URL(url), obje.toString(), headers, Constants.HTTP_REQUEST.POST.name());
        } catch (MalformedURLException ex) {
            Logger.getLogger(CommonUtil.class.getName()).log(Level.SEVERE, null, ex);
        }

        JSONObject resobj = new JSONObject((String) apiResponse);
        JSONObject data = resobj.getJSONObject("data");
        boolean isValid = false;
        try {
            resobj.getJSONObject("data").getJSONObject("me");
            isValid = true;
        } catch (JSONException e) {
        }

        if (isValid) {
            email = data.getJSONObject("me").getString("email");
        }
        return email;
    }

    public static boolean checkProdDB(HttpServletRequest request) {
        // Generate 6-Digit Key
        String referrer = request.getHeader("domain");
        if (null == referrer) {
            referrer = request.getHeader("referer");
            if (null == referrer) {
                referrer = "";
            }
        }
        Logger.getLogger(CommonUtil.class.getName()).log(Level.SEVERE,
                "Url referer");
        Logger.getLogger(CommonUtil.class.getName()).log(Level.SEVERE,
                referrer);
        return referrer.startsWith("https://www.hegmakart.com/") || referrer.startsWith("https://hegmakart.com/");
    }
}
