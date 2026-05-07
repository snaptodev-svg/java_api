/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.dmart.util;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.dmart.model.Error;
import com.sun.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;

/**
 * Class contains generic methods that perform tasks like fetching URLs, Logging
 * Errors
 *
 * @version 1.0
 * @since Jul 04, 2022
 * @author Rahul(QMM Technologies Pvt. Ltd.)
 */
public final class HTTPUtil {

    public static String getUrl(String sUrl) {

        try {
            URL url = new URL(sUrl);
            return getUrl(url);
        } catch (MalformedURLException urlException) {
            HTTPUtil.displayException(urlException);
            return "-";
        }
    }

    public static String getUrl(URL sUrl) {

        StringBuilder urlResult = new StringBuilder();
        try {
            HttpURLConnection connection = (HttpURLConnection) sUrl.openConnection();
            connection.setRequestProperty("Accept", "application/json");

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            connection.getInputStream()))) {
                String inputLine;
                while ((inputLine = reader.readLine()) != null) {
                    urlResult.append(inputLine);
                }
            }

        } catch (MalformedURLException urlException) {
            HTTPUtil.displayException(urlException);
            return "-";
        } catch (IOException ioException) {
            HTTPUtil.displayException(ioException);
            return "-";
        }

        return urlResult.toString();
    }

    /**
     *
     * @param fURL
     * @param requestBody
     * @param headers
     * @return
     */
    public static String executeUrl(URL fURL, Object requestBody,
            ArrayList<BasicNameValuePair> headers, String httpRequest) {
        StringBuilder result = new StringBuilder();
        try {
            byte[] requestObj = (null != requestBody) ? requestBody.toString()
                    .getBytes() : null;
            // HTTP request
            HttpURLConnection urlConnection = (HttpURLConnection) fURL
                    .openConnection();
            urlConnection
                    .setRequestMethod(httpRequest);
            urlConnection.setDoInput(true);
            urlConnection.setDoOutput(null != requestObj);
            if (null != headers) {

                for (int i = 0; i < headers.size(); i++) {

                    urlConnection.setRequestProperty(headers.get(i).getName(),
                            headers.get(i).getValue());
                }
            }
            urlConnection.connect();

            // Send request message?
            if (null != requestObj) {
                OutputStream outputStream = urlConnection
                        .getOutputStream();
                outputStream.write(requestObj);
                outputStream.flush();
            }

            //Status
            int arg = urlConnection.getResponseCode();
            if (200 != arg) {

                return "-";
            }

            // Stream response
            int bufferLength = 0;
            byte[] buffer = new byte[1024];
            try (InputStream inputStream = urlConnection.getInputStream()) {
                while (0 < (bufferLength = inputStream.read(buffer))) {
                    String temp = new String(buffer, 0, bufferLength);
                    result.append(temp);
                    buffer = new byte[1024];
                }
            }

            // Close connection
            urlConnection.disconnect();
            return result.toString();
        } catch (IOException e) {
            HTTPUtil.displayException(e);
            return "-";
        }
    }

    public static String processUrlConnectionReq(String pBankData, String pBankUrl) throws Exception {
        URL vUrl;
        URLConnection vHttpUrlConnection;
        DataOutputStream vPrintout;
        DataInputStream vInput = null;
        StringBuffer vStringBuffer = null;
        vUrl = new URL(pBankUrl);

        if (vUrl.openConnection() instanceof HttpsURLConnection) {
            vHttpUrlConnection = (HttpsURLConnection) vUrl.openConnection();
        } else if (vUrl.openConnection() instanceof com.sun.net.ssl.HttpsURLConnection) {
            vHttpUrlConnection = (com.sun.net.ssl.HttpsURLConnection) vUrl.openConnection();
        } else {
            vHttpUrlConnection = (URLConnection) vUrl.openConnection();
        }
        vHttpUrlConnection.setDoInput(true);
        vHttpUrlConnection.setDoOutput(true);
        vHttpUrlConnection.setUseCaches(false);
        vHttpUrlConnection.connect();
        vPrintout = new DataOutputStream(vHttpUrlConnection.getOutputStream());
        vPrintout.writeBytes(pBankData);
        vPrintout.flush();
        vPrintout.close();
        try {
            try (BufferedReader bufferedreader = new BufferedReader(new InputStreamReader(vHttpUrlConnection.getInputStream()))) {
                vStringBuffer = new StringBuffer();
                String vRespData;
                while ((vRespData = bufferedreader.readLine()) != null) {
                    if (vRespData.length() != 0) {
                        vStringBuffer.append(vRespData.trim());
                    }
                }
            }
        } catch(IOException e) {
            System.out.println();
        } finally {
            if (vInput != null) {
                vInput.close();
            }
            if (vHttpUrlConnection != null) {
                vHttpUrlConnection = null;
            }
        }
        return vStringBuffer.toString();
    }

    /**
     * Calls the given URL, serializes the JSON to the given class
     *
     * @param url
     * @param token
     * @return
     */
    public static <T> T fetchAndSerializeToList(String url, TypeToken token) {

        String apiResponse = null;
        try {
            apiResponse = getUrl(url);
            displayException(new Exception(url));
        } catch (Exception ex) {
            //Do Nothing
        }

        try {
            Error err = new GsonBuilder().create().fromJson(apiResponse,
                    new TypeToken<Error>() {
                    }.getType());
            //Not a valid error object
            if (null == err.getErrorCode()) {

                throw new UnsupportedOperationException("Non error class found!");
            }
            return (T) err;
        } catch (JsonSyntaxException | UnsupportedOperationException e2) {
            try {
                Object output;
                try {
                    //If response is JSONArray
                    JSONArray arr = new JSONArray(apiResponse);
                    output = new GsonBuilder().create().fromJson(
                            String.valueOf(arr.get(0)), token.getType());
                } catch (JSONException | JsonSyntaxException e) {
                    //If response is JSONObject
                    output = new GsonBuilder().create().fromJson(
                            apiResponse, token.getType());
                }
                return (T) output;
            } catch (JsonSyntaxException e) {
                return (T) (new Error("0", "Unknown Error!"));
            }
        }
    }

    /**
     * Calls the given URL, serializes the JSON to the given class
     *
     * @param <T>
     * @param url
     * @param request
     * @param token
     * @param headers
     * @param httpRequest
     * @return
     */
    public static <T> T fetchAndSerializeToPostList(String url, Object request, TypeToken token,
            ArrayList<BasicNameValuePair> headers, String httpRequest) {

        String apiResponse = null;
        try {
            displayRequest(request);
            apiResponse = executeUrl(new URL(url), request, headers, httpRequest);
            displayResponse(apiResponse);
        } catch (MalformedURLException ex) {
            //Do Nothing
        }

        try {
            Error err = new GsonBuilder().create().fromJson(apiResponse,
                    new TypeToken<Error>() {
                    }.getType());
            //Not a valid error object
            if (null == err.getErrorCode()) {

                throw new UnsupportedOperationException("Non error class found!");
            }
            return (T) err;
        } catch (JsonSyntaxException | UnsupportedOperationException e2) {
            try {
                Object output;
                try {
                    //If response is JSONArray
                    JSONArray arr = new JSONArray(apiResponse);
                    output = new GsonBuilder().create().fromJson(
                            String.valueOf(arr.get(0)), token.getType());
                } catch (JsonSyntaxException | JSONException e) {
                    //If response is JSONObject
                    output = new GsonBuilder().create().fromJson(
                            apiResponse, token.getType());
                }
                return (T) output;
            } catch (JsonSyntaxException e) {
                return (T) (new Error("0", "Unknown Error!"));
            }
        }
    }

    /**
     * Logs messages based on input Exception type.
     *
     * @param object Exception object
     */
    public static void displayException(Exception object) {
        Logger.getLogger(HTTPUtil.class.getName()).log(Level.SEVERE,
                object.getMessage());
    }

    public static void displayRequest(Object object) {
        try {
            Logger.getLogger("Hegma-Request").log(Level.SEVERE,
                    object.toString());
        } catch (Exception e) {
        }
    }

    public static void displayResponse(String object) {
        try {
            Logger.getLogger("Hegma-Response").log(Level.SEVERE,
                    object);
        } catch (Exception e) {
        }
    }
}
