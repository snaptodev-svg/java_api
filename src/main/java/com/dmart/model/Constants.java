/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.dmart.model;

/**
 * @version 1.0
 * @since Jul 04, 2022
 * @author Rahul(QMM Technologies Pvt. Ltd.)
 */
public class Constants {

    public static final boolean DEBUG = false;
    
    public static final String KEY_EMAIL = "email";
    public static final String KEY_MOBILE = "mobile";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_DOMAIN = "domain";
    public static final String KEY_KEY = "key";
    public static final String KEY_NEW_PASSWORD = "new_password";
    public static final String KEY_AGENT_ID = "agent_id";
    public static final String KEY_CHECKOUT_TOKEN = "checkout_token";
    public static final String KEY_CHECKOUT_ID = "checkout_id";
    public static final String KEY_ORDER_NO = "order_no";
    public static final String METAKEY_RAZOR_ORDER_ID = "razor_order";
    public static final String KEY_OTP = "otp";
    public static final String KEY_VARIANT_ID = "variant_id";
    public static final String KEY_PRODUCT_ID = "product_id";
    public static final String KEY_POSTAL_CODE = "postal_code";
    public static final String KEY_TOKEN = "token";
    public static final String KEY_FIRST_NAME = "first_name";
    public static final String KEY_LAST_NAME = "last_name";
    public static final String KEY_SEARCH_TERM = "search_term";
    public static final String KEY_ORDER_ID = "order_id";
    public static final String KEY_ORDER_LINE_ID = "order_line_id";
    public static final String KEY_RATING = "rating";
    public static final String KEY_REVIEW = "review";
    public static final String KEY_PRODUCT_NAME = "productName";
    public static final String KEY_REMARK = "remark";
    public static final String KEY_STATUS = "status";
    
    public static final String KEY_REPLACE_OTP = "{otp}";
    public static final String KEY_REPLACE_ORDER_NO = "{order_no}";
    public static final String KEY_REPLACE_ORDER_DATE = "{order_date}";
    public static final String KEY_REPLACE_ORDER_TOTAL_PRICE = "{order_total_price}";
    public static final String KEY_REPLACE_NAME = "{name}";
    public static final String KEY_REPLACE_ADDRESS = "{address}";
    public static final String KEY_REPLACE_COUNTRY = "{country}";
    public static final String KEY_REPLACE_PRODUCT_URL = "{product_url}";
    public static final String KEY_REPLACE_PRODUCT_NAME = "{product_name}";
    public static final String KEY_REPLACE_PRODUCT_QUANTITY = "{product_quantity}";
    public static final String KEY_REPLACE_PRODUCT_PRICE = "{product_price}";
    public static final String KEY_REPLACE_TOTAL_QUANTITY = "{total_quantity}";
    public static final String KEY_REPLACE_SUBTOTAL_PRICE = "{subtotal_price}";
    public static final String KEY_REPLACE_SHIPPING_PRICE = "{shipping_price}";
    public static final String KEY_REPLACE_DISCOUNT_PRICE = "{discount_price}";
    public static final String KEY_REPLACE_VOUCHER_CODE = "{voucher_code}";
    public static final String KEY_REPLACE_WEIGHTED_PRICE = "{weighted_price}";
    public static final String KEY_REPLACE_TRACKING_INFORMATION = "{tracking_information}";
    public static final String KEY_REPLACE_PRODUCT_STATUS = "{product_status}";
    public static final String KEY_REPLACE_REMARK = "{remarks}";
    public static final String KEY_REPLACE_DELIVERY_TIME = "{delivery_time}";
    
    public static final String HEGMA_DOMAIN = "@snapto.in";
    public static final String HEGMA_PASSWORD = "hegmaDefault@123";
    public static final String HEGMA_ENC_PASSWORD = "pbkdf2_sha256$260000$NqSTWKPJDVhlVdbfOd7I8p$58QIeXL4iFLzsvaFyxsgAg7D3zy3iUhQQdh1WJSJ5XE=";

    /**
     * Four parameters for this enum, us request_params as constants to
     * parameter key, use response code for success/error reponse
     */
    public static enum REQUEST_RESPONSE {

        SUCCESS(0, "success", "Success!"),
        EMAIL(1, KEY_EMAIL, "Missing Email!"),
        PASSWORD(2, KEY_PASSWORD, "Missing Password!"),
        DOMAIN(3, KEY_DOMAIN, "Missing Domain!"),
        BALANCE(4, "balance", "Insuffcient Balance!"),
        UNKNOWN_ERROR(5, "error", "Unknown Error!"),
        INVALID_PARAMETERS(6, "params", "Invalid Parameters!"),
        AGENT_ID(16, KEY_AGENT_ID, "Missing Agent Id!"),
        KEY(7, KEY_KEY, "Invalid Access Key!"),
        NEW_PASSWORD(8, KEY_NEW_PASSWORD, "Invalid Password!"),
        SERVICE_DISABLED(19, "service_disabled", "Service is disabled!"),
        INVALID_AGENT_ID(20, KEY_AGENT_ID, "Agent Does not Exist!"),
        INVALID_LOGIN(21, "LOGIN_FAILED", "Login Failed!");

        public final int request_code;
        public final String request_param;
        public final int response_code;
        public final String response_message;

        private REQUEST_RESPONSE(int code, String request_param,
                String response_message) {
            this.request_code = code;
            this.request_param = request_param;
            this.response_code = code;
            this.response_message = response_message;
        }

        public static REQUEST_RESPONSE errorValueForRequest(String paramCode) {
            if (paramCode.equals(KEY_EMAIL)) {
                return EMAIL;
            } else if (paramCode.equals(KEY_PASSWORD)) {
                return PASSWORD;
            } else if (paramCode.equals(KEY_DOMAIN)) {
                return DOMAIN;
            } else if (paramCode.equals(KEY_KEY)) {
                return KEY;
            } else if (paramCode.equals(KEY_NEW_PASSWORD)) {
                return NEW_PASSWORD;
            } else if (paramCode.equals(KEY_AGENT_ID)) {
                return AGENT_ID;
            } else {
                return INVALID_PARAMETERS;
            }
        }
    }

    public static enum HTTP_REQUEST {

        GET,
        PUT,
        POST,
        DELETE;
    }
    
    public static final String MSG91_OTP_DLE_ID = "1007798996029149193";
    public static final String MSG91_ORDER_DLE_ID = "1007316412780564316";
    public static final String MSG91_DELIVERY_DLE_ID = "1007170778604617101";
    public static final String MSG91_SHIPMENT_DLE_ID = "1007116103197820760";
    
    public static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";
    
    public static final String HEGMA_API_URL = "https://www.snapto.in/graphql/";
    public static final String HEGMA_BETA_API_URL = "https://www.snapto.in/graphql/";
    public static final String HEGMA_API_AUTH_TOKEN = "Bearer U5afPUU6LJ6Ro2gOmqCDn03uRObpXQ";
    
    public static final String RAZORPAY_PAYMENT_ID = "razorpay_payment_id";
    public static final String RAZORPAY_ORDER_ID = "razorpay_order_id";
    public static final String RAZORPAY_SIGNATURE = "razorpay_signature";
    public static final String RAZORPAY_SECRET_KEY = "p8VSj8ZEbUGGrpsyDD5ClD49";
    public static final String RAZORPAY_API_URL = "https://api.razorpay.com/v1/orders";
    public static final String RAZORPAY_API_AUTH_TOKEN = "Basic cnpwX3Rlc3RfR0VacW9oVW55Z2F5WWI6cDhWU2o4WkViVUdHcnBzeURENUNsRDQ5";
    
    public static final String CC_STATUS_API_URL = DEBUG? "https://apitest.ccavenue.com/apis/servlet/DoWebTrans" : 
            "https://api.ccavenue.com/apis/servlet/DoWebTrans";
    public static final String CF_API_URL = DEBUG? "https://sandbox.cashfree.com/pg/orders" : 
            "https://api.cashfree.com/pg/orders";
    public static final String CALLBACK_URL = DEBUG? "http://localhost/dmart_staging/v1/dmart/checkOrder" : 
            "https://www.snapto.in/mrpcart/v1/dmart/checkOrder";
    public static final String REDIRECT_URL = DEBUG? "http://localhost/dmart_staging/" : 
            "https://www.snapto.in/mrpcart/";
    public static final String CF_CLIENT_ID = DEBUG? "TEST102641336069404a63667da2ae1633146201" : 
            "92543726c3348c787e43947201734529";
    public static final String CF_CLIENT_SECRET = DEBUG? "cfsk_ma_test_4482abe442c7d9d0dc5077e48cac3158_36e7ae01": 
            "cfsk_ma_prod_1fec444140dbf5e689249ba63dc6128f_2a270bef";
    public static final String CF_API_VERSION = DEBUG? "2023-08-01": 
            "2023-08-01";
    public static final String CC_COMMAND = "orderStatusTracker";
    public static final String CC_REQ_TYPE = "JSON";
    public static final String CC_RES_TYPE = "JSON";
    public static final String CC_VERSION = "1.2";
}
