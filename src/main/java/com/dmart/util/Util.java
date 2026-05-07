/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.dmart.util;

import com.dmart.model.Constants.REQUEST_RESPONSE;
import com.dmart.model.Error;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @version 1.0
 * @since Jul 04, 2022
 * @author Rahul(QMM Technologies Pvt. Ltd.)
 */
public class Util {

    /**
     * Pass the parameters key name defined in Constants file and a JSON object
     * to check those keys in
     *
     * @param jsonBody
     * @param validationParamsKey
     * @return
     */
    public static Object validateParamsInJSON(String jsonBody,
            String... validationParamsKey) {

        try {
            JSONObject body = new JSONObject(jsonBody);
            Error err = null;
            for (String validationParamsKey1 : validationParamsKey) {
                Object val = body.get(validationParamsKey1);
                if (null == val || "".equals(val.toString())) {

                    err = new Error(String.valueOf(
                            REQUEST_RESPONSE.INVALID_PARAMETERS.response_code),
                            REQUEST_RESPONSE.INVALID_PARAMETERS.response_message);
                    break;
                } else if ((val instanceof Integer || val instanceof Long)
                        && 0 == Long.parseLong(val.toString())) {

                    err = new Error(String.valueOf(
                            REQUEST_RESPONSE.errorValueForRequest(val.toString()).response_code),
                            REQUEST_RESPONSE.errorValueForRequest(val.toString()).response_message);
                    break;
                } else if ((val instanceof Float || val instanceof Double)
                        && 0.0d == Double.parseDouble(val.toString())) {

                    err = new Error(String.valueOf(
                            REQUEST_RESPONSE.errorValueForRequest(val.toString()).response_code),
                            REQUEST_RESPONSE.errorValueForRequest(val.toString()).response_message);
                    break;
                }
            }

            if (null != err) {

                return err;
            } else {

                return true;
            }
        } catch (JSONException | NumberFormatException e) {
            return new Error(String.valueOf(
                    REQUEST_RESPONSE.INVALID_PARAMETERS.response_code),
                    REQUEST_RESPONSE.INVALID_PARAMETERS.response_message);
        }
    }

    /**
     * Pass the parameters key name defined in Constants file and a JSON object
     * to check those keys in
     *
     * @param validationParams
     * @return
     */
    public static Object validateAgentParams(Object... validationParams) {

        try {
            Error err = null;
            for (Object val : validationParams) {
                if (null == val || "".equals(val.toString())) {

                    err = new Error(String.valueOf(
                            REQUEST_RESPONSE.INVALID_PARAMETERS.response_code),
                            REQUEST_RESPONSE.INVALID_PARAMETERS.response_message);
                    break;
                } else if ((val instanceof Integer || val instanceof Long)
                        && 0 == Long.parseLong(val.toString())) {

                    err = new Error(String.valueOf(
                            REQUEST_RESPONSE.errorValueForRequest(val.toString()).response_code),
                            REQUEST_RESPONSE.errorValueForRequest(val.toString()).response_message);
                    break;
                } else if ((val instanceof Float || val instanceof Double)
                        && 0.0d == Double.parseDouble(val.toString())) {

                    err = new Error(String.valueOf(
                            REQUEST_RESPONSE.errorValueForRequest(val.toString()).response_code),
                            REQUEST_RESPONSE.errorValueForRequest(val.toString()).response_message);
                    break;
                }
            }

            if (null != err) {

                return err;
            } else {

                return true;
            }
        } catch (JSONException | NumberFormatException e) {
            return new Error(String.valueOf(
                    REQUEST_RESPONSE.INVALID_PARAMETERS.response_code),
                    REQUEST_RESPONSE.INVALID_PARAMETERS.response_message);
        }
    }
}
