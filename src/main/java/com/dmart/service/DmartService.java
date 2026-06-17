/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.dmart.service;

import com.dmart.db.ServiceDao;
import com.dmart.model.Constants;
import com.dmart.model.CustomerRequest;
import com.dmart.model.EmailConstants;
import com.dmart.model.Error;
import com.dmart.model.LoginError;
import com.dmart.model.LoginResponse;
import com.dmart.model.OrderRequest;
import com.dmart.model.PgRequest;
import com.dmart.model.User;
import com.dmart.util.CommonUtil;
import com.dmart.util.HTTPUtil;
import com.dmart.util.SMSUtility;
import com.dmart.util.SendSmtpMail;
import com.dmart.util.Util;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.MessagingException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author Rahul
 */
@Path("/dmart")
public class DmartService implements Filter {

    @Context
    HttpServletRequest request;

    public DmartService() {
        // TODO Auto-generated constructor stub
    }

    @GET
    @Path("/pgInit")
    @Produces({MediaType.APPLICATION_JSON})
    public Response pgInIt(@QueryParam(Constants.KEY_CHECKOUT_TOKEN) String checkoutToken) {

        boolean isProdDB = CommonUtil.checkProdDB(request);
        String sessionId = "";
        String resData = "{checkout(token: \"" + checkoutToken + "\") {id,user{email},totalPrice{gross{amount}}}}";

        JSONObject obje = new JSONObject();
        obje.put("query", resData);

        ArrayList<BasicNameValuePair> headers = new ArrayList<>();
        headers.add(0, new BasicNameValuePair("Authorization",
                Constants.HEGMA_API_AUTH_TOKEN));
        headers.add(0, new BasicNameValuePair("Content-Type",
                "application/json"));

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
            Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
        }

        JSONObject resobj = new JSONObject((String) apiResponse);
        JSONObject checkout = resobj.getJSONObject("data").getJSONObject("checkout");
        if (checkout.has("id")) {

            checkoutToken = checkoutToken + "_" + System.currentTimeMillis();
            double amount = checkout.getJSONObject("totalPrice").getJSONObject("gross").getDouble("amount");
            String email = checkout.getJSONObject("user").getString("email");
            String callback_url = Constants.CALLBACK_URL + "?order_id=" + checkoutToken;
            ArrayList<HashMap<String, String>> userData = ServiceDao.accountUserEmailData(email, isProdDB);

            OrderRequest or = new OrderRequest();
            or.setReturn_url(callback_url);

            CustomerRequest cr = new CustomerRequest();
            cr.setCustomer_id((String) userData.get(0).get("id"));
            cr.setCustomer_phone((String) userData.get(0).get("mobile_no"));

            PgRequest req = new PgRequest();
            req.setOrder_amount(amount);
            req.setOrder_id(checkoutToken);
            req.setOrder_currency("INR");
            req.setOrder_meta(or);
            req.setCustomer_details(cr);

            Gson gson = new Gson();

            headers = new ArrayList<>();
            headers.add(0, new BasicNameValuePair("x-api-version",
                    Constants.CF_API_VERSION));
            headers.add(0, new BasicNameValuePair("Content-Type",
                    "application/json"));
            headers.add(0, new BasicNameValuePair("x-client-id",
                    Constants.CF_CLIENT_ID));
            headers.add(0, new BasicNameValuePair("x-client-secret",
                    Constants.CF_CLIENT_SECRET));

            url = Constants.CF_API_URL;
            try {
                apiResponse = HTTPUtil.executeUrl(new URL(url), gson.toJson(req), headers, Constants.HTTP_REQUEST.POST.name());
            } catch (MalformedURLException ex) {
                Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
            }

            try {
                resobj = new JSONObject((String) apiResponse);
                sessionId = resobj.getString("payment_session_id");
                ServiceDao.insertOrderPayment(sessionId, checkoutToken, String.valueOf(amount), isProdDB);
            } catch (Exception e) {
                ArrayList<HashMap<String, String>> orderPaymentData = ServiceDao.orderPaymentData(checkoutToken, isProdDB);
                for (int i = 0; i < orderPaymentData.size(); i++) {
                    HashMap<String, String> map = orderPaymentData.get(i);
                    sessionId = (String) map.get("session_id");
                }
            }
        }
        return Response.temporaryRedirect(URI.create(Constants.REDIRECT_URL + "home.jsp?encRequest=" + sessionId)).build();
    }

    @POST
    @Path("/pgComplete")
    @Produces({MediaType.APPLICATION_JSON})
    public Response pgComplete(String jsonData) {

        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                "MrpCartRequest pg complete data");
        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                jsonData);

        boolean isProdDB = CommonUtil.checkProdDB(request);
        JSONObject obj = new JSONObject(jsonData);
        String checkoutToken = obj.getJSONObject("data").getJSONObject("order").getString("order_id");
        String orderStatus = obj.getJSONObject("data").getJSONObject("payment").getString("payment_status");
        String referenceNo = obj.getJSONObject("data").getJSONObject("payment").getString("cf_payment_id");
        String orderid = obj.getJSONObject("data").getJSONObject("order").getString("order_id");
        String bankReferenceNo = obj.getJSONObject("data").getJSONObject("payment").getString("cf_payment_id");

        String message = "";
        String resData = "{checkout(token: \"" + checkoutToken + "\") {id,totalPrice{gross{amount}},privateMetadata{key,value},metadata{key,value}}}";

        JSONObject obje = new JSONObject();
        obje.put("query", resData);

        ArrayList<BasicNameValuePair> headers = new ArrayList<>();
        headers.add(0, new BasicNameValuePair("Authorization",
                Constants.HEGMA_API_AUTH_TOKEN));
        headers.add(0, new BasicNameValuePair("Content-Type",
                "application/json"));

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
            Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
        }

        JSONObject resobj = new JSONObject((String) apiResponse);
        JSONObject checkout = resobj.getJSONObject("data").getJSONObject("checkout");
        if (checkout.has("id")) {
            String checkoutId = checkout.getString("id");
            double amount3 = checkout.getJSONObject("totalPrice").getJSONObject("gross").getDouble("amount");
            boolean isOrderValid = false;
            ArrayList<HashMap<String, String>> orderPaymentData = ServiceDao.orderPaymentData(orderid, isProdDB);
            for (int i = 0; i < orderPaymentData.size(); i++) {
                HashMap<String, String> map = orderPaymentData.get(i);
                double amount2 = Double.parseDouble((String) map.get("amount"));
                String checkoutToken1 = (String) map.get("checkout_token");
                if (amount3 == amount2 && checkoutToken.equals(checkoutToken1)) {
                    isOrderValid = true;
                }
            }
            if (orderStatus.equals("SUCCESS") && isOrderValid) {
                resData = "mutation {orderCreateFromCheckout(id:\"" + checkoutId + "\") {order{id,number,metadata{key,value},userEmail,user{id},voucher{code},created,total{gross{amount}},undiscountedTotal{gross{amount}},"
                        + "shippingAddress{firstName,lastName,streetAddress1,streetAddress2,city,cityArea,country {country},postalCode,phone},"
                        + "lines{variantName,weightedShipping,productName,quantity,thumbnail{url},unitPrice{gross{amount}}},shippingPrice{gross{amount}},"
                        + "discounts{amount{amount}},subtotal{gross{amount}}},errors{code,message}}}";
                obje = new JSONObject();
                obje.put("query", resData);

                try {
                    apiResponse = HTTPUtil.executeUrl(new URL(url), obje.toString(), headers, Constants.HTTP_REQUEST.POST.name());
                } catch (MalformedURLException ex) {
                    Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
                }

                resobj = new JSONObject((String) apiResponse);
                JSONArray errors = resobj.getJSONObject("data").getJSONObject("orderCreateFromCheckout").getJSONArray("errors");
                if (errors.length() == 0) {
                    JSONObject order = resobj.getJSONObject("data").getJSONObject("orderCreateFromCheckout").getJSONObject("order");
                    String orderId = order.getString("id");
                    String orderNo = order.getString("number");
                    String orderDate = order.getString("created").substring(0, 10);
                    String name = order.getJSONObject("shippingAddress").getString("firstName") + " " + order.getJSONObject("shippingAddress").getString("lastName");
                    String address = order.getJSONObject("shippingAddress").getString("streetAddress1") + ", " + order.getJSONObject("shippingAddress").getString("streetAddress2");
                    String country = order.getJSONObject("shippingAddress").getString("city") + ", " + order.getJSONObject("shippingAddress").getJSONObject("country").getString("country") + " " + order.getJSONObject("shippingAddress").getString("postalCode");
                    JSONArray lines = order.getJSONArray("lines");
                    String productData = "";
                    int totalQuantity = 0;
                    double weightedShipping = 0;
                    DecimalFormat df = new DecimalFormat("#0.00");
                    String shippingamount = String.valueOf(df.format(order.getJSONObject("shippingPrice").getJSONObject("gross").getDouble("amount")));
                    double subtotalamount = 0;
                    String discountamount = "0.00";
                    String voucherCode = "";
                    String Date = "";
                    String slot = "";
                    JSONArray metaArr1 = order.getJSONArray("metadata");
                    for (int i = 0; i < metaArr1.length(); i++) {
                        JSONObject data = metaArr1.getJSONObject(i);
                        if (data.getString("key").equalsIgnoreCase("Date")) {
                            Date = data.getString("value");
                        }
                        if (data.getString("key").equalsIgnoreCase("Time")) {
                            slot = data.getString("value");
                        }
                    }
                    try {
                        // Define the desired date format
                        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd");
                        SimpleDateFormat outputFormat = new SimpleDateFormat("dd-MM-yyyy");
                        Date date = inputFormat.parse(orderDate);
                        orderDate = outputFormat.format(date);
                    } catch (Exception e) {
                    }
                    try {
                        discountamount = String.valueOf(df.format(order.getJSONObject("undiscountedTotal").getJSONObject("gross").getDouble("amount") - order.getJSONObject("total").getJSONObject("gross").getDouble("amount")));
                        voucherCode = " (" + order.getJSONObject("voucher").getString("code") + ")";
                    } catch (Exception e) {
                    }
                    boolean isLogin = false;
                    try {
                        JSONObject user = resobj.getJSONObject("data").getJSONObject("orderCreateFromCheckout").getJSONObject("order").getJSONObject("user");
                        isLogin = true;
                    } catch (Exception e) {
                    }

                    String mobile = order.getJSONObject("shippingAddress").getString("phone");
                    mobile = mobile.substring(3, mobile.length());
                    String email = resobj.getJSONObject("data").getJSONObject("orderCreateFromCheckout").getJSONObject("order").getString("userEmail");
                    double amount = resobj.getJSONObject("data").getJSONObject("orderCreateFromCheckout").getJSONObject("order").getJSONObject("total").getJSONObject("gross").getDouble("amount");         
                    String msg = "Your order no. " + orderNo + " of Rs " + df.format(amount) + " is confirmed. It will be delivered as per your selected date and time slot. Thank You - Snapto by SNAP ECOMMERCE";
                    String[] bccrecipients = {"anuj@qmmtech.com"};
                    String[] recipients = {email};
                    try {       
                        SMSUtility.SendTransactionalSMSMSG91ROCKET(mobile, msg, Constants.MSG91_ORDER_DLE_ID);
                        String emailMessage = EmailConstants.EMAIL_HEAD + EmailConstants.EMAIL_ORDER_BODY1.replace(Constants.KEY_REPLACE_DELIVERY_TIME, Date + " " + slot)
                                + EmailConstants.EMAIL_ORDER_BODY_SUMMARY.replace(Constants.KEY_REPLACE_ORDER_NO, orderNo).replace(Constants.KEY_REPLACE_ORDER_DATE, orderDate).replace(Constants.KEY_REPLACE_ORDER_TOTAL_PRICE, String.valueOf(df.format(amount)))
                                + EmailConstants.EMAIL_ORDER_BODY_SHIPPING_ADDRESS.replace(Constants.KEY_REPLACE_NAME, name).replace(Constants.KEY_REPLACE_ADDRESS, address).replace(Constants.KEY_REPLACE_COUNTRY, country)
                                + EmailConstants.EMAIL_ORDER_BODY_PRODUCT_HEADER;

                        for (int i = 0; i < lines.length(); i++) {
                            JSONObject data = lines.getJSONObject(i);
                            double amt = data.getJSONObject("unitPrice").getJSONObject("gross").getDouble("amount");
                            String wgtShippPrc = null;
                            try {
                                subtotalamount = subtotalamount + (data.getJSONObject("unitPrice").getJSONObject("gross").getDouble("amount") * data.getInt("quantity"));
                                wgtShippPrc = data.getString("weightedShipping");
                            } catch (Exception e) {
                            }

                            if (null == wgtShippPrc) {
                            } else {
                                double wgtamt = Double.parseDouble(wgtShippPrc) * data.getInt("quantity");
                                amt = amt - Double.parseDouble(wgtShippPrc);
                                weightedShipping = weightedShipping + wgtamt;
                            }
                            productData = productData + EmailConstants.EMAIL_ORDER_BODY_PRODUCT_DATA.replace(Constants.KEY_REPLACE_PRODUCT_URL, data.getJSONObject("thumbnail").getString("url")).
                                    replace(Constants.KEY_REPLACE_PRODUCT_NAME, data.getString("productName") + " - " + data.getString("variantName")).replace(Constants.KEY_REPLACE_PRODUCT_QUANTITY, String.valueOf(data.getInt("quantity"))).
                                    replace(Constants.KEY_REPLACE_PRODUCT_PRICE, String.valueOf(df.format(amt)));
                            totalQuantity = totalQuantity + data.getInt("quantity");
                        }

                        subtotalamount = Double.parseDouble(df.format(subtotalamount - weightedShipping));
                        emailMessage = emailMessage + productData;
                          sendAdminNotification(orderNo, email, name, mobile, amount, Date, slot, "Online");
                        emailMessage = emailMessage + EmailConstants.EMAIL_ORDER_BODY_PRODUCT_TOTAL.replace(Constants.KEY_REPLACE_TOTAL_QUANTITY, String.valueOf(totalQuantity)).
                                replace(Constants.KEY_REPLACE_SUBTOTAL_PRICE, String.valueOf(df.format(subtotalamount))).replace(Constants.KEY_REPLACE_SHIPPING_PRICE, shippingamount).
                                replace(Constants.KEY_REPLACE_WEIGHTED_PRICE, String.valueOf(df.format(weightedShipping))).replace(Constants.KEY_REPLACE_DISCOUNT_PRICE, discountamount).
                                replace(Constants.KEY_REPLACE_ORDER_TOTAL_PRICE, String.valueOf(df.format(amount))).replace(Constants.KEY_REPLACE_VOUCHER_CODE, voucherCode)
                                + EmailConstants.EMAIL_FOOTER;
                      
                        SendSmtpMail.sendSSLMessagewithBcc(recipients, bccrecipients, "Your Snapto order " + orderNo + " Placed", emailMessage);
                    } catch (MessagingException | UnsupportedEncodingException ex) {
                        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    String reference = checkoutToken;

                    resData = "mutation {orderMarkAsPaid(id: \"" + orderId + "\", transactionReference: \"" + reference + "\") {errors{message}}}";
                    obje = new JSONObject();
                    obje.put("query", resData);

                    try {
                        HTTPUtil.executeUrl(new URL(url), obje.toString(), headers, Constants.HTTP_REQUEST.POST.name());
                        ServiceDao.updateOrderReference(referenceNo, bankReferenceNo, checkoutToken, isProdDB);
                        message = "Success";
                    } catch (MalformedURLException ex) {
                        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }
        if (message.equals("Success")) {
            ServiceDao.updateOrderPaymentStatus(orderid, "Success", isProdDB);
            // Send admin notification
          
            return Response.temporaryRedirect(URI.create(Constants.REDIRECT_URL + "success.jsp")).build();
        } else {
            ServiceDao.updateOrderPaymentStatus(orderid, "Failed", isProdDB);
            return Response.temporaryRedirect(URI.create(Constants.REDIRECT_URL + "failure.jsp")).build();
        }
    }

    @GET
    @Path("/checkOrder")
    @Produces({MediaType.APPLICATION_JSON})
    public Response checkOrder(@QueryParam(Constants.KEY_ORDER_ID) String orderId) {

        String message = "";
        boolean isProdDB = CommonUtil.checkProdDB(request);
        ArrayList headers = new ArrayList<>();
        headers.add(0, new BasicNameValuePair("x-api-version",
                Constants.CF_API_VERSION));
        headers.add(0, new BasicNameValuePair("Content-Type",
                "application/json"));
        headers.add(0, new BasicNameValuePair("x-client-id",
                Constants.CF_CLIENT_ID));
        headers.add(0, new BasicNameValuePair("x-client-secret",
                Constants.CF_CLIENT_SECRET));

        String url = Constants.CF_API_URL + "/" + orderId + "/payments";
        String apiResponse = "[]";
        try {
            apiResponse = HTTPUtil.executeUrl(new URL(url), null, headers, Constants.HTTP_REQUEST.GET.name());
        } catch (MalformedURLException ex) {
            Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
        }

        String orderId2 = orderId.split("_")[0];
        JSONArray resobj = new JSONArray((String) apiResponse);
        String status = resobj.getJSONObject(0).getString("payment_status");
        if (status.equals("SUCCESS")) {
            String resData = "{checkout(token: \"" + orderId2 + "\") {id,totalPrice{gross{amount}},privateMetadata{key,value},metadata{key,value}}}";

            JSONObject obje = new JSONObject();
            obje.put("query", resData);

            headers = new ArrayList<>();
            headers.add(0, new BasicNameValuePair("Authorization",
                    Constants.HEGMA_API_AUTH_TOKEN));
            headers.add(0, new BasicNameValuePair("Content-Type",
                    "application/json"));

            if (isProdDB) {
                url = Constants.HEGMA_API_URL;
            } else {
                url = Constants.HEGMA_BETA_API_URL;
            }
            try {
                apiResponse = HTTPUtil.executeUrl(new URL(url), obje.toString(), headers, Constants.HTTP_REQUEST.POST.name());
            } catch (MalformedURLException ex) {
                Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
            }

            JSONObject resobj1 = new JSONObject((String) apiResponse);
            JSONObject checkout = resobj1.getJSONObject("data").getJSONObject("checkout");
            if (checkout.has("id")) {
                String checkoutId = checkout.getString("id");
                double amount3 = checkout.getJSONObject("totalPrice").getJSONObject("gross").getDouble("amount");
                boolean isOrderValid = false;
                ArrayList<HashMap<String, String>> orderPaymentData = ServiceDao.orderPaymentData(orderId, isProdDB);
                for (int i = 0; i < orderPaymentData.size(); i++) {
                    HashMap<String, String> map = orderPaymentData.get(i);
                    double amount2 = Double.parseDouble((String) map.get("amount"));
                    String checkoutToken1 = (String) map.get("checkout_token");
                    if (orderId.equals(checkoutToken1)) {
                        isOrderValid = true;
                    }
                }
                if (isOrderValid) {
                    resData = "mutation {orderCreateFromCheckout(id:\"" + checkoutId + "\") {order{id,number,metadata{key,value},userEmail,user{id},voucher{code},created,total{gross{amount}},undiscountedTotal{gross{amount}},"
                            + "shippingAddress{firstName,lastName,streetAddress1,streetAddress2,city,cityArea,country {country},postalCode,phone},"
                            + "lines{variantName,weightedShipping,productName,quantity,thumbnail{url},unitPrice{gross{amount}}},shippingPrice{gross{amount}},"
                            + "discounts{amount{amount}},subtotal{gross{amount}}},errors{code,message}}}";
                    obje = new JSONObject();
                    obje.put("query", resData);

                    try {
                        apiResponse = HTTPUtil.executeUrl(new URL(url), obje.toString(), headers, Constants.HTTP_REQUEST.POST.name());
                    } catch (MalformedURLException ex) {
                        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
                    }

                    resobj1 = new JSONObject((String) apiResponse);
                    JSONArray errors = resobj1.getJSONObject("data").getJSONObject("orderCreateFromCheckout").getJSONArray("errors");
                    if (errors.length() == 0) {
                        JSONObject order = resobj1.getJSONObject("data").getJSONObject("orderCreateFromCheckout").getJSONObject("order");
                        String orderId1 = order.getString("id");
                        String orderNo = order.getString("number");
                        String orderDate = order.getString("created").substring(0, 10);
                        String name = order.getJSONObject("shippingAddress").getString("firstName") + " " + order.getJSONObject("shippingAddress").getString("lastName");
                        String address = order.getJSONObject("shippingAddress").getString("streetAddress1") + ", " + order.getJSONObject("shippingAddress").getString("streetAddress2");
                        String country = order.getJSONObject("shippingAddress").getString("city") + ", " + order.getJSONObject("shippingAddress").getJSONObject("country").getString("country") + " " + order.getJSONObject("shippingAddress").getString("postalCode");
                        JSONArray lines = order.getJSONArray("lines");
                        String productData = "";
                        int totalQuantity = 0;
                        double weightedShipping = 0;
                        DecimalFormat df = new DecimalFormat("#0.00");
                        String shippingamount = String.valueOf(df.format(order.getJSONObject("shippingPrice").getJSONObject("gross").getDouble("amount")));
                        double subtotalamount = 0;
                        String discountamount = "0.00";
                        String voucherCode = "";
                        String Date = "";
                        String slot = "";
                        JSONArray metaArr1 = order.getJSONArray("metadata");
                        for (int i = 0; i < metaArr1.length(); i++) {
                            JSONObject data = metaArr1.getJSONObject(i);
                            if (data.getString("key").equalsIgnoreCase("Date")) {
                                Date = data.getString("value");
                            }
                            if (data.getString("key").equalsIgnoreCase("Time")) {
                                slot = data.getString("value");
                            }
                        }
                        try {
                            // Define the desired date format
                            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd");
                            SimpleDateFormat outputFormat = new SimpleDateFormat("dd-MM-yyyy");
                            Date date = inputFormat.parse(orderDate);
                            orderDate = outputFormat.format(date);
                        } catch (Exception e) {
                        }
                        try {
                            discountamount = String.valueOf(df.format(order.getJSONObject("undiscountedTotal").getJSONObject("gross").getDouble("amount") - order.getJSONObject("total").getJSONObject("gross").getDouble("amount")));
                            voucherCode = " (" + order.getJSONObject("voucher").getString("code") + ")";
                        } catch (Exception e) {
                        }
                        boolean isLogin = false;
                        try {
                            JSONObject user = resobj1.getJSONObject("data").getJSONObject("orderCreateFromCheckout").getJSONObject("order").getJSONObject("user");
                            isLogin = true;
                        } catch (Exception e) {
                        }

                        String mobile = order.getJSONObject("shippingAddress").getString("phone");
                        mobile = mobile.substring(3, mobile.length());
                        String email = resobj1.getJSONObject("data").getJSONObject("orderCreateFromCheckout").getJSONObject("order").getString("userEmail");
                        double amount = resobj1.getJSONObject("data").getJSONObject("orderCreateFromCheckout").getJSONObject("order").getJSONObject("total").getJSONObject("gross").getDouble("amount");
//                    if (!isLogin) {
//                        JSONArray metaArr1 = checkout.getJSONArray("metadata");
//                        for (int i = 0; i < metaArr1.length(); i++) {
//                            JSONObject data = metaArr1.getJSONObject(i);
//                            if (data.getString("key").equalsIgnoreCase("mobile")) {
//                                mobile = data.getString("value");
//                            }
//                        }
//                    } else {
//                        mobile = ServiceDao.getMobile(email);
//                    }
                        String msg = "Your order no. " + orderNo + " of Rs " + df.format(amount) + " is confirmed. It will be delivered as per your selected date and time slot. Thank You - Snapto by SNAP ECOMMERCE";
                        String[] bccrecipients = {"anuj@qmmtech.com"};
                        String[] recipients = {email};
                        try {
                            SMSUtility.SendTransactionalSMSMSG91ROCKET(mobile, msg, Constants.MSG91_ORDER_DLE_ID);
                              sendAdminNotification(orderNo, email, name, mobile, amount, Date, slot, "Online");
                            String emailMessage = EmailConstants.EMAIL_HEAD + EmailConstants.EMAIL_ORDER_BODY1.replace(Constants.KEY_REPLACE_DELIVERY_TIME, Date + " " + slot)
                                    + EmailConstants.EMAIL_ORDER_BODY_SUMMARY.replace(Constants.KEY_REPLACE_ORDER_NO, orderNo).replace(Constants.KEY_REPLACE_ORDER_DATE, orderDate).replace(Constants.KEY_REPLACE_ORDER_TOTAL_PRICE, String.valueOf(df.format(amount)))
                                    + EmailConstants.EMAIL_ORDER_BODY_SHIPPING_ADDRESS.replace(Constants.KEY_REPLACE_NAME, name).replace(Constants.KEY_REPLACE_ADDRESS, address).replace(Constants.KEY_REPLACE_COUNTRY, country)
                                    + EmailConstants.EMAIL_ORDER_BODY_PRODUCT_HEADER;

                            for (int i = 0; i < lines.length(); i++) {
                                JSONObject data = lines.getJSONObject(i);
                                double amt = data.getJSONObject("unitPrice").getJSONObject("gross").getDouble("amount");
                                String wgtShippPrc = null;
                                try {
                                    subtotalamount = subtotalamount + (data.getJSONObject("unitPrice").getJSONObject("gross").getDouble("amount") * data.getInt("quantity"));
                                    wgtShippPrc = data.getString("weightedShipping");
                                } catch (Exception e) {
                                }

                                if (null == wgtShippPrc) {
                                } else {
                                    double wgtamt = Double.parseDouble(wgtShippPrc) * data.getInt("quantity");
                                    amt = amt - Double.parseDouble(wgtShippPrc);
                                    weightedShipping = weightedShipping + wgtamt;
                                }
                                productData = productData + EmailConstants.EMAIL_ORDER_BODY_PRODUCT_DATA.replace(Constants.KEY_REPLACE_PRODUCT_URL, data.getJSONObject("thumbnail").getString("url")).
                                        replace(Constants.KEY_REPLACE_PRODUCT_NAME, data.getString("productName") + " - " + data.getString("variantName")).replace(Constants.KEY_REPLACE_PRODUCT_QUANTITY, String.valueOf(data.getInt("quantity"))).
                                        replace(Constants.KEY_REPLACE_PRODUCT_PRICE, String.valueOf(df.format(amt)));
                                totalQuantity = totalQuantity + data.getInt("quantity");
                            }

                            subtotalamount = Double.parseDouble(df.format(subtotalamount - weightedShipping));
                            emailMessage = emailMessage + productData;
                            emailMessage = emailMessage + EmailConstants.EMAIL_ORDER_BODY_PRODUCT_TOTAL.replace(Constants.KEY_REPLACE_TOTAL_QUANTITY, String.valueOf(totalQuantity)).
                                    replace(Constants.KEY_REPLACE_SUBTOTAL_PRICE, String.valueOf(df.format(subtotalamount))).replace(Constants.KEY_REPLACE_SHIPPING_PRICE, shippingamount).
                                    replace(Constants.KEY_REPLACE_WEIGHTED_PRICE, String.valueOf(df.format(weightedShipping))).replace(Constants.KEY_REPLACE_DISCOUNT_PRICE, discountamount).
                                    replace(Constants.KEY_REPLACE_ORDER_TOTAL_PRICE, String.valueOf(df.format(amount))).replace(Constants.KEY_REPLACE_VOUCHER_CODE, voucherCode)
                                    + EmailConstants.EMAIL_FOOTER;
                            SendSmtpMail.sendSSLMessagewithBcc(recipients, bccrecipients, "Your Snapto order " + orderNo + " Placed", emailMessage);
                        } catch (MessagingException | UnsupportedEncodingException ex) {
                            Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        String reference = orderId1;

                        resData = "mutation {orderMarkAsPaid(id: \"" + orderId1 + "\", transactionReference: \"" + reference + "\") {errors{message}}}";
                        obje = new JSONObject();
                        obje.put("query", resData);

                        try {
                            HTTPUtil.executeUrl(new URL(url), obje.toString(), headers, Constants.HTTP_REQUEST.POST.name());
                            ServiceDao.updateOrderReference("", "", orderId2, isProdDB);
                            message = "Success";
                        } catch (MalformedURLException ex) {
                            Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }
            }
            if (message.equals("Success")) {
                ServiceDao.updateOrderPaymentStatus(orderId, "Success", isProdDB);
                  return Response.temporaryRedirect(URI.create(Constants.REDIRECT_URL + "success.jsp")).build();
            } else {
                ServiceDao.updateOrderPaymentStatus(orderId, "Failed", isProdDB);
                return Response.temporaryRedirect(URI.create(Constants.REDIRECT_URL + "failure.jsp")).build();
            }
        } else {
            ServiceDao.updateOrderPaymentStatus(orderId, "Failed", isProdDB);
            return Response.temporaryRedirect(URI.create(Constants.REDIRECT_URL + "failure.jsp")).build();
        }
    }

    @POST
    @Path("/razorpayCompleteCod")
    @Produces({MediaType.APPLICATION_JSON})
    public Response razorpayCompleteCod(String jsonData) {

        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                "MrpCartRequest import Raw data");
        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                jsonData);

        boolean isProdDB = CommonUtil.checkProdDB(request);
        String message = "";
        //Validate params
        Object output = Util.validateParamsInJSON(jsonData,
                Constants.KEY_CHECKOUT_TOKEN);
        if (output instanceof Error) {

            return Response.ok().entity(message).build();
        }

        //Params
        JSONObject obj = new JSONObject(jsonData);
        String checkoutToken = obj.getString(Constants.KEY_CHECKOUT_TOKEN);
        String resData = "{checkout(token: \"" + checkoutToken + "\") {id,privateMetadata{key,value},metadata{key,value}}}";

        JSONObject obje = new JSONObject();
        obje.put("query", resData);

        ArrayList<BasicNameValuePair> headers = new ArrayList<>();
        headers.add(0, new BasicNameValuePair("Authorization",
                Constants.HEGMA_API_AUTH_TOKEN));
        headers.add(0, new BasicNameValuePair("Content-Type",
                "application/json"));

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
            Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
        }

        JSONObject resobj = new JSONObject((String) apiResponse);
        JSONObject checkout = resobj.getJSONObject("data").getJSONObject("checkout");
        if (checkout.has("id")) {

            String checkoutId = checkout.getString("id");
            resData = "mutation {orderCreateFromCheckout(id:\"" + checkoutId + "\") {order{id,number,metadata{key,value},userEmail,user{id},voucher{code},created,total{gross{amount}},undiscountedTotal{gross{amount}},"
                    + "shippingAddress{firstName,lastName,streetAddress1,streetAddress2,city,cityArea,country {country},postalCode,phone},"
                    + "lines{variantName,weightedShipping,productName,quantity,thumbnail{url},unitPrice{gross{amount}}},shippingPrice{gross{amount}},"
                    + "discounts{amount{amount}},subtotal{gross{amount}}},errors{code,message}}}";
            obje = new JSONObject();
            obje.put("query", resData);

            try {
                apiResponse = HTTPUtil.executeUrl(new URL(url), obje.toString(), headers, Constants.HTTP_REQUEST.POST.name());
            } catch (MalformedURLException ex) {
                Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
            }

            resobj = new JSONObject((String) apiResponse);
            JSONArray errors = resobj.getJSONObject("data").getJSONObject("orderCreateFromCheckout").getJSONArray("errors");
            if (errors.length() == 0) {
                JSONObject order = resobj.getJSONObject("data").getJSONObject("orderCreateFromCheckout").getJSONObject("order");
                String orderNo = order.getString("number");
                String orderDate = order.getString("created").substring(0, 10);
                String name = order.getJSONObject("shippingAddress").getString("firstName") + " " + order.getJSONObject("shippingAddress").getString("lastName");
                String address = order.getJSONObject("shippingAddress").getString("streetAddress1") + ", " + order.getJSONObject("shippingAddress").getString("streetAddress2");
                String country = order.getJSONObject("shippingAddress").getString("city") + ", " + order.getJSONObject("shippingAddress").getJSONObject("country").getString("country") + " " + order.getJSONObject("shippingAddress").getString("postalCode");
                JSONArray lines = order.getJSONArray("lines");
                String productData = "";
                int totalQuantity = 0;
                double weightedShipping = 0;
                DecimalFormat df = new DecimalFormat("#0.00");
                String shippingamount = String.valueOf(df.format(order.getJSONObject("shippingPrice").getJSONObject("gross").getDouble("amount")));
                double subtotalamount = 0;
                String discountamount = "0.00";
                String voucherCode = "";
                String Date = "";
                String slot = "";
                JSONArray metaArr1 = order.getJSONArray("metadata");
                for (int i = 0; i < metaArr1.length(); i++) {
                    JSONObject data = metaArr1.getJSONObject(i);
                    if (data.getString("key").equalsIgnoreCase("Date")) {
                        Date = data.getString("value");
                    }
                    if (data.getString("key").equalsIgnoreCase("Time")) {
                        slot = data.getString("value");
                    }
                }
                try {
                    // Define the desired date format
                    SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd");
                    SimpleDateFormat outputFormat = new SimpleDateFormat("dd-MM-yyyy");
                    Date date = inputFormat.parse(orderDate);
                    orderDate = outputFormat.format(date);
                } catch (Exception e) {
                }
                try {
                    discountamount = String.valueOf(df.format(order.getJSONObject("undiscountedTotal").getJSONObject("gross").getDouble("amount") - order.getJSONObject("total").getJSONObject("gross").getDouble("amount")));
                    voucherCode = " (" + order.getJSONObject("voucher").getString("code") + ")";
                } catch (Exception e) {
                }
                boolean isLogin = false;
                try {
                    JSONObject user = resobj.getJSONObject("data").getJSONObject("orderCreateFromCheckout").getJSONObject("order").getJSONObject("user");
                    isLogin = true;
                } catch (Exception e) {
                }

                String mobile = order.getJSONObject("shippingAddress").getString("phone");
                mobile = mobile.substring(3, mobile.length());
                String email = order.getString("userEmail");
                double amount = order.getJSONObject("total").getJSONObject("gross").getDouble("amount");
     
                String msg = "Your order no. " + orderNo + " of Rs " + df.format(amount) + " is confirmed. It will be delivered as per your selected date and time slot. Thank You - Snapto by SNAP ECOMMERCE";
                SMSUtility.SendTransactionalSMSMSG91ROCKET(mobile, msg, Constants.MSG91_ORDER_DLE_ID);
                String[] bccrecipients = {"anuj@qmmtech.com"};
                String[] recipients = {email};
                try {
                    String emailMessage = EmailConstants.EMAIL_HEAD + EmailConstants.EMAIL_ORDER_BODY1.replace(Constants.KEY_REPLACE_DELIVERY_TIME, Date + " " + slot)
                            + EmailConstants.EMAIL_ORDER_BODY_SUMMARY.replace(Constants.KEY_REPLACE_ORDER_NO, orderNo).replace(Constants.KEY_REPLACE_ORDER_DATE, orderDate).replace(Constants.KEY_REPLACE_ORDER_TOTAL_PRICE, String.valueOf(df.format(amount)))
                            + EmailConstants.EMAIL_ORDER_BODY_SHIPPING_ADDRESS.replace(Constants.KEY_REPLACE_NAME, name).replace(Constants.KEY_REPLACE_ADDRESS, address).replace(Constants.KEY_REPLACE_COUNTRY, country)
                            + EmailConstants.EMAIL_ORDER_BODY_PRODUCT_HEADER;

                    for (int i = 0; i < lines.length(); i++) {
                        JSONObject data = lines.getJSONObject(i);
                        double amt = data.getJSONObject("unitPrice").getJSONObject("gross").getDouble("amount");
                        String wgtShippPrc = null;
                        try {
                            subtotalamount = subtotalamount + (data.getJSONObject("unitPrice").getJSONObject("gross").getDouble("amount") * data.getInt("quantity"));
                            wgtShippPrc = data.getString("weightedShipping");
                        } catch (Exception e) {
                        }

                        if (null == wgtShippPrc) {
                        } else {
                            double wgtamt = Double.parseDouble(wgtShippPrc) * data.getInt("quantity");
                            amt = amt - Double.parseDouble(wgtShippPrc);
                            weightedShipping = weightedShipping + wgtamt;
                        }
                        productData = productData + EmailConstants.EMAIL_ORDER_BODY_PRODUCT_DATA.replace(Constants.KEY_REPLACE_PRODUCT_URL, data.getJSONObject("thumbnail").getString("url")).
                                replace(Constants.KEY_REPLACE_PRODUCT_NAME, data.getString("productName") + " - " + data.getString("variantName")).replace(Constants.KEY_REPLACE_PRODUCT_QUANTITY, String.valueOf(data.getInt("quantity"))).
                                replace(Constants.KEY_REPLACE_PRODUCT_PRICE, String.valueOf(df.format(amt)));
                        totalQuantity = totalQuantity + data.getInt("quantity");
                    }

                    subtotalamount = Double.parseDouble(df.format(subtotalamount - weightedShipping));
                    emailMessage = emailMessage + productData;
                    emailMessage = emailMessage + EmailConstants.EMAIL_ORDER_BODY_PRODUCT_TOTAL.replace(Constants.KEY_REPLACE_TOTAL_QUANTITY, String.valueOf(totalQuantity)).
                            replace(Constants.KEY_REPLACE_SUBTOTAL_PRICE, String.valueOf(df.format(subtotalamount))).replace(Constants.KEY_REPLACE_SHIPPING_PRICE, shippingamount).
                            replace(Constants.KEY_REPLACE_WEIGHTED_PRICE, String.valueOf(df.format(weightedShipping))).replace(Constants.KEY_REPLACE_DISCOUNT_PRICE, discountamount).
                            replace(Constants.KEY_REPLACE_ORDER_TOTAL_PRICE, String.valueOf(df.format(amount))).replace(Constants.KEY_REPLACE_VOUCHER_CODE, voucherCode)
                            + EmailConstants.EMAIL_FOOTER;
                    SendSmtpMail.sendSSLMessagewithBcc(recipients, bccrecipients, "Your Snapto order " + orderNo + " Placed", emailMessage);
                } catch (MessagingException | UnsupportedEncodingException ex) {
                    Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
                }
                message = "Success";
            }
        }
        return Response.ok().entity(message).build();
    }

   @POST
@Path("/sendOTP")
@Produces({MediaType.APPLICATION_JSON})
public Response sendOTP(String jsonData) {
    JSONObject responseObj = new JSONObject();
    
    try {
        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                "MrpCartRequest send otp data: " + jsonData);

        // Validate params
        Object output = Util.validateParamsInJSON(jsonData, Constants.KEY_MOBILE);
        if (output instanceof Error) {
            responseObj.put("status", "error");
            responseObj.put("code", 400);
            responseObj.put("message", "Invalid request: missing mobile number");
            responseObj.put("error", ((Error) output).toString());
            return Response.status(400).entity(responseObj.toString()).build();
        }

        // Parse request
        JSONObject obj = new JSONObject(jsonData);
        String mobile = obj.getString(Constants.KEY_MOBILE);

        // Validate mobile format
        if (mobile == null || mobile.trim().isEmpty()) {
            responseObj.put("status", "error");
            responseObj.put("code", 400);
            responseObj.put("message", "Mobile number cannot be empty");
            return Response.status(400).entity(responseObj.toString()).build();
        }

        if (mobile.length() < 10) {
            responseObj.put("status", "error");
            responseObj.put("code", 400);
            responseObj.put("message", "Mobile number must be at least 10 digits");
            return Response.status(400).entity(responseObj.toString()).build();
        }

        // Generate OTP
        String otp = CommonUtil.generateOTP();
        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                "Generated OTP: " + otp + " for mobile: " + mobile);

        boolean isProdDB = CommonUtil.checkProdDB(request);
        
        // Try to insert OTP in database
        boolean dbInsertStatus = false;
        try {
            dbInsertStatus = ServiceDao.insertMobileOtp(mobile, otp, isProdDB);
        } catch (Exception dbEx) {
            Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                    "Database error while inserting OTP: " + dbEx.getMessage());
            // Continue even if database fails - OTP is still generated
            responseObj.put("warning", "Database error, but OTP generated: " + otp);
        }

        if (dbInsertStatus) {
            // OTP saved successfully, send via SMS/Email
            // String msg = otp + " is the OTP to login to your Snapto Account by SNAP ECOMMERCE"; 
            String msg = "Your Snapto login OTP is "+otp+". It is valid for 10 minutes. Do not share it with anyone. #OaIKlhn7qya";
            boolean smsSent = false;
            boolean emailSent = false;

            if (mobile.length() == 10) {
                // Send SMS
                try {
                    SMSUtility.SendTransactionalSMSMSG91ROCKET(mobile, msg, Constants.MSG91_OTP_DLE_ID);
                    smsSent = true;
                    Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                            "SMS sent successfully to: " + mobile);
                } catch (Exception smsEx) {
                    Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                            "SMS Error: " + smsEx.getMessage());
                    responseObj.put("sms_error", smsEx.getMessage());
                }
            } else if (mobile.contains("@")) {
                // Send Email
                String[] recipients = {mobile};
                try {
                    String emailMessage = EmailConstants.EMAIL_HEAD
                            + EmailConstants.EMAIL_LOGIN_BODY.replace(Constants.KEY_REPLACE_OTP, otp)
                            + EmailConstants.EMAIL_FOOTER;
                    SendSmtpMail.sendSSLMessage(recipients, "Login OTP", emailMessage);
                    emailSent = true;
                    Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                            "Email sent successfully to: " + mobile);
                } catch (MessagingException | UnsupportedEncodingException emailEx) {
                    Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                            "Email Error: " + emailEx.getMessage());
                    responseObj.put("email_error", emailEx.getMessage());
                }
            }

            // Success response
            responseObj.put("status", "success");
            responseObj.put("code", 200);
            responseObj.put("message", "OTP sent successfully");
            responseObj.put("mobile", mobile);
            responseObj.put("otp_length", otp.length());
            responseObj.put("sms_sent", smsSent);
            responseObj.put("email_sent", emailSent);
            responseObj.put("note", "OTP has been sent to the provided contact");
            
            return Response.ok(responseObj.toString()).build();
        } else {
            // Database insert failed
            responseObj.put("status", "error");
            responseObj.put("code", 500);
            responseObj.put("message", "Failed to store OTP in database");
            responseObj.put("mobile", mobile);
            responseObj.put("debug_otp", otp); // For testing only
            return Response.status(500).entity(responseObj.toString()).build();
        }

    } catch (JSONException jsonEx) {
        responseObj.put("status", "error");
        responseObj.put("code", 400);
        responseObj.put("message", "Invalid JSON format");
        responseObj.put("error", jsonEx.getMessage());
        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                "JSON Parse Error: " + jsonEx.getMessage());
        return Response.status(400).entity(responseObj.toString()).build();
    } catch (Exception ex) {
        responseObj.put("status", "error");
        responseObj.put("code", 500);
        responseObj.put("message", "Internal server error");
        responseObj.put("error", ex.getMessage());
        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                "Error: " + ex.getMessage());
        ex.printStackTrace();
        return Response.status(500).entity(responseObj.toString()).build();
    }
}

    @POST
    @Path("/verifyOTP")
    @Produces({MediaType.APPLICATION_JSON})
    public Response verifyOTP(String jsonData) {

        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                "MrpCartRequest verify otp data");
        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                jsonData);

        LoginResponse lr = new LoginResponse();
        lr.setApi_status(false);
        ArrayList<LoginError> arr = new ArrayList<>();
        LoginError le = new LoginError();
        //Validate params
        Object output = Util.validateParamsInJSON(jsonData,
                Constants.KEY_MOBILE, Constants.KEY_OTP);
        if (output instanceof Error) {
            le.setField("1");
            le.setMessage("Please enter valid data.");
            arr.add(le);
            lr.setErrors(arr);
            return Response.ok().entity(lr).build();
        }

        boolean isProdDB = CommonUtil.checkProdDB(request);
        //Params
        JSONObject obj = new JSONObject(jsonData);
        String mobile = obj.getString(Constants.KEY_MOBILE);
        String otp = obj.getString(Constants.KEY_OTP);
        ArrayList<HashMap<String, String>> data = ServiceDao.verifyOtp(mobile, otp, isProdDB);
        if (data.isEmpty()) {
            le.setField("1");
            le.setMessage("Please enter valid OTP.");
            arr.add(le);
            lr.setErrors(arr);
            return Response.ok().entity(lr).build();
        }

        boolean status = ServiceDao.updateMobileOTPStatus(mobile, otp, isProdDB);
        lr.setApi_status(status);

        ArrayList<BasicNameValuePair> headers = new ArrayList<>();
        headers.add(0, new BasicNameValuePair("Content-Type",
                "application/json"));
        String resData;
        JSONObject obje;
        String apiResponse = "";
        String url;
        if (isProdDB) {
            url = Constants.HEGMA_API_URL;
        } else {
            url = Constants.HEGMA_BETA_API_URL;
        }
        JSONObject resobj;
        String email;
        if (mobile.length() == 10) {
            email = mobile + Constants.HEGMA_DOMAIN;
        } else {
            email = mobile;
        }

        if (status) {
            ArrayList<HashMap<String, String>> userData;
            if (mobile.length() == 10) {
                userData = ServiceDao.accountUserData(mobile, isProdDB);
            } else {
                userData = ServiceDao.accountUserEmailData(mobile, isProdDB);
            }
            if (userData.isEmpty()) {
                resData = "mutation{accountRegister(input: {email: \"" + email + "\", password: \"" + Constants.HEGMA_PASSWORD + "\"}){errors{message}user{email}}}";
                obje = new JSONObject();
                obje.put("query", resData);
                try {
                    apiResponse = HTTPUtil.executeUrl(new URL(url), obje.toString(), headers, Constants.HTTP_REQUEST.POST.name());
                } catch (MalformedURLException ex) {
                    Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
                }
                resobj = new JSONObject((String) apiResponse);
                JSONArray errors = resobj.getJSONObject("data").getJSONObject("accountRegister").getJSONArray("errors");
                if (errors.length() == 0) {
                    ServiceDao.updateUserMobile(mobile, email, isProdDB);
                } else {
                    le.setField("1");
                    le.setMessage(errors.getJSONObject(0).getString("message"));
                    arr.add(le);
                    lr.setErrors(arr);
                    lr.setApi_status(false);
                    return Response.ok().entity(lr).build();
                }
            } else {
                if (userData.size() > 0) {
                    for (int ii = 0; ii < userData.size(); ii++) {
                        email = userData.get(ii).get("email");
                    }
                }
                if (mobile.length() == 10) {
                    ServiceDao.updateUserPassword(mobile, isProdDB);
                } else {
                    ServiceDao.updateUserEmailPassword(email, Constants.HEGMA_ENC_PASSWORD, isProdDB);
                }
            }

            resData = "mutation TokenAuth {tokenCreate(email: \"" + email + "\", password: \"" + Constants.HEGMA_PASSWORD
                    + "\") {errors {field,message},token,refreshToken,user {id,firstName,lastName,email}}}";
            obje = new JSONObject();
            obje.put("query", resData);
            try {
                apiResponse = HTTPUtil.executeUrl(new URL(url), obje.toString(), headers, Constants.HTTP_REQUEST.POST.name());
            } catch (MalformedURLException ex) {
                Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
            }
            resobj = new JSONObject((String) apiResponse);
            JSONArray errors = resobj.getJSONObject("data").getJSONObject("tokenCreate").getJSONArray("errors");
            if (errors.length() == 0) {
                User user = new User();
                user.setFirstName(resobj.getJSONObject("data").getJSONObject("tokenCreate").getJSONObject("user").getString("firstName"));
                user.setLastName(resobj.getJSONObject("data").getJSONObject("tokenCreate").getJSONObject("user").getString("lastName"));
                user.setId(resobj.getJSONObject("data").getJSONObject("tokenCreate").getJSONObject("user").getString("id"));
                user.setEmail(email);

                lr.setToken(resobj.getJSONObject("data").getJSONObject("tokenCreate").getString("token"));
                lr.setRefreshToken(resobj.getJSONObject("data").getJSONObject("tokenCreate").getString("refreshToken"));
                lr.setUser(user);

                ServiceDao.updateUserEmailPassword(email, "", isProdDB);

            } else {
                le.setField("1");
                le.setMessage(errors.getJSONObject(0).getString("message"));
                arr.add(le);
                lr.setErrors(arr);
                lr.setApi_status(false);
            }
        }
        return Response.ok().entity(lr).build();
    }

    @POST
    @Path("/bulkDiscounts")
    @Produces({MediaType.APPLICATION_JSON})
    public Response bulkDiscounts(String jsonData) {

        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                "MrpCartRequest bulk discount data");
        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                jsonData);

        boolean isProdDB = CommonUtil.checkProdDB(request);
        //Validate params
        Object output = Util.validateParamsInJSON(jsonData,
                Constants.KEY_VARIANT_ID);
        if (output instanceof Error) {

            return Response.ok().entity("").build();
        }

        //Params
        JSONObject obj = new JSONObject(jsonData);
        String variantId = obj.getString(Constants.KEY_VARIANT_ID);
        String resData = "{vouchers(first:50,filter:{metadata:{key:\"variant\",value:\"" + variantId + "\"}}){edges{node{id,code,minCheckoutItemsQuantity,channelListings{channel{name},discountValue},discountValueType}}}}";

        JSONObject obje = new JSONObject();
        obje.put("query", resData);

        ArrayList<BasicNameValuePair> headers = new ArrayList<>();
        headers.add(0, new BasicNameValuePair("Authorization",
                Constants.HEGMA_API_AUTH_TOKEN));
        headers.add(0, new BasicNameValuePair("Content-Type",
                "application/json"));

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
            Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
        }

        return Response.ok().entity(apiResponse).build();
    }

    @POST
    @Path("/productDetails")
    @Produces({MediaType.APPLICATION_JSON})
    public Response productDetails(String jsonData) {

        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                "MrpCartRequest product details data");
        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                jsonData);

        //Validate params
        Object output = Util.validateParamsInJSON(jsonData,
                Constants.KEY_PRODUCT_ID);
        if (output instanceof Error) {

            return Response.ok().entity("").build();
        }

        boolean isProdDB = CommonUtil.checkProdDB(request);
        //Params
        JSONObject obj = new JSONObject(jsonData);
        String productId = obj.getString(Constants.KEY_PRODUCT_ID);
        String resData = "{product(id: \"" + productId + "\", channel: \"default-channel\") {id,purchaseNote,name,description,seoDescription,rating,productType{id,hasVariants},category{name,id,slug,level,parent{name}},thumbnail{url},attributes{attribute{id,name},values{id,name,file{url}}},pricing{priceRangeUndiscounted{start{net{amount}}},priceRange{start{net{amount},gross{amount}}}},media{url},variants{id,weightedShipping,originalPrice,name,media{url},quantityAvailable,pricing{price{net{amount}},priceUndiscounted{net{amount}}}}}}";

        JSONObject obje = new JSONObject();
        obje.put("query", resData);

        ArrayList<BasicNameValuePair> headers = new ArrayList<>();
        headers.add(0, new BasicNameValuePair("Content-Type",
                "application/json"));

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
            Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
        }

        JSONObject resobj = new JSONObject((String) apiResponse);
        JSONArray arr = resobj.getJSONObject("data").getJSONObject("product").getJSONArray("variants");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject data = arr.getJSONObject(i);
            resData = "{vouchers(first:50,filter:{metadata:{key:\"variant\",value:\"" + data.getString("id") + "\"}}){edges{node{id,code,minCheckoutItemsQuantity,channelListings{channel{name},discountValue},discountValueType}}}}";

            obje = new JSONObject();
            obje.put("query", resData);

            headers = new ArrayList<>();
            headers.add(0, new BasicNameValuePair("Authorization",
                    Constants.HEGMA_API_AUTH_TOKEN));
            headers.add(0, new BasicNameValuePair("Content-Type",
                    "application/json"));

            try {
                apiResponse = HTTPUtil.executeUrl(new URL(url), obje.toString(), headers, Constants.HTTP_REQUEST.POST.name());
            } catch (MalformedURLException ex) {
                Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
            }

            JSONObject resobj1 = new JSONObject((String) apiResponse);
            JSONArray arr1 = resobj1.getJSONObject("data").getJSONObject("vouchers").getJSONArray("edges");
            JSONArray vouArr = new JSONArray();
            for (int j = 0; j < arr1.length(); j++) {
                JSONObject data1 = arr1.getJSONObject(j);
                String voucherText = "Buy " + data1.getJSONObject("node").getInt("minCheckoutItemsQuantity") + " or above and save ";
                double discountValue = 0;
                JSONArray arr2 = data1.getJSONObject("node").getJSONArray("channelListings");
                for (int k = 0; k < arr2.length(); k++) {
                    JSONObject data2 = arr2.getJSONObject(k);
                    if (data2.getJSONObject("channel").getString("name").equals("Hegma Channel")) {
                        discountValue = data2.getDouble("discountValue");
                    }
                }
                if (data1.getJSONObject("node").getString("discountValueType").equals("PERCENTAGE")) {
                    voucherText = voucherText + discountValue + "%";
                } else {
                    voucherText = voucherText + "Rs " + discountValue;
                }
                JSONObject item1 = new JSONObject();
                item1.put("voucherText", voucherText);
                vouArr.put(item1);
            }
            data.put("voucherTextArr", vouArr);
        }
        return Response.ok().entity(resobj.toString()).build();
    }

    @POST
    @Path("/checkPostalCode")
    @Produces({MediaType.APPLICATION_JSON})
    public Response checkPostalCode(String jsonData) {

        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                "MrpCartRequest check postal code data");
        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                jsonData);

        //Validate params
        Object output = Util.validateParamsInJSON(jsonData,
                Constants.KEY_POSTAL_CODE);
        if (output instanceof Error) {

            return Response.ok().entity("").build();
        }

        boolean isProdDB = CommonUtil.checkProdDB(request);
        //Params
        JSONObject obj = new JSONObject(jsonData);
        int postalCode = Integer.parseInt(obj.getString(Constants.KEY_POSTAL_CODE));
        String resData = "{shippingZones(first:50){edges{node{id,name,shippingMethods{id,name,postalCodeRules{inclusionType,start,end}}}}}}";

        JSONObject obje = new JSONObject();
        obje.put("query", resData);

        ArrayList<BasicNameValuePair> headers = new ArrayList<>();
        headers.add(0, new BasicNameValuePair("Authorization",
                Constants.HEGMA_API_AUTH_TOKEN));
        headers.add(0, new BasicNameValuePair("Content-Type",
                "application/json"));

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
            Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
        }

        boolean status = false;
        JSONObject resobj = new JSONObject((String) apiResponse);
        JSONArray arr = resobj.getJSONObject("data").getJSONObject("shippingZones").getJSONArray("edges");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject data = arr.getJSONObject(i);
            JSONArray arr1 = data.getJSONObject("node").getJSONArray("shippingMethods");
            for (int j = 0; j < arr1.length(); j++) {
                JSONObject data1 = arr1.getJSONObject(j);
                JSONArray arr2 = data1.getJSONArray("postalCodeRules");
                if (arr2.length() == 0) {
                    status = true;
                    break;
                }
                for (int k = 0; k < arr2.length(); k++) {
                    JSONObject data2 = arr2.getJSONObject(k);
                    if (data2.getString("inclusionType").equals("INCLUDE")) {
                        int start = Integer.parseInt(data2.getString("start"));
                        int end = Integer.parseInt(data2.getString("end"));
                        if (postalCode >= start && postalCode <= end) {
                            status = true;
                            break;
                        }
                    }
                    if (data2.getString("inclusionType").equals("EXCLUDE")) {
                        int start = Integer.parseInt(data2.getString("start"));
                        int end = Integer.parseInt(data2.getString("end"));
                        if (!(postalCode >= start && postalCode <= end)) {
                            status = true;
                            break;
                        }
                    }
                }
            }
        }
        if (status) {
            return Response.ok().entity("success").build();
        }
        return Response.ok().entity("").build();
    }

    @POST
    @Path("/setpassword")
    @Produces({MediaType.APPLICATION_JSON})
    public Response setPassword(String jsonData) {

        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                "MrpCartRequest set password data");
        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                jsonData);

        //Validate params
        Object output = Util.validateParamsInJSON(jsonData,
                Constants.KEY_TOKEN, Constants.KEY_NEW_PASSWORD);
        if (output instanceof Error) {

            return Response.ok().entity("").build();
        }

        boolean isProdDB = CommonUtil.checkProdDB(request);
        //Params
        JSONObject obj = new JSONObject(jsonData);
        String token = obj.getString(Constants.KEY_TOKEN);
        String newPassword = obj.getString(Constants.KEY_NEW_PASSWORD);
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
            Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
        }

        JSONObject resobj = new JSONObject((String) apiResponse);
        JSONObject data = resobj.getJSONObject("data");
        boolean isValid = true;
        try {
            String me = resobj.getJSONObject("data").getString("me");
            isValid = false;
        } catch (JSONException e) {
        }

        if (isValid) {
            String email = data.getJSONObject("me").getString("email");
            ServiceDao.updateUserEmailPassword(email, Constants.HEGMA_ENC_PASSWORD, isProdDB);
            resData = "mutation{passwordChange(newPassword: \"" + newPassword + "\", oldPassword: \"" + Constants.HEGMA_PASSWORD + "\") {user{id},errors{message}}}";

            obje = new JSONObject();
            obje.put("query", resData);

            try {
                apiResponse = HTTPUtil.executeUrl(new URL(url), obje.toString(), headers, Constants.HTTP_REQUEST.POST.name());
            } catch (MalformedURLException ex) {
                Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
            }

            resobj = new JSONObject((String) apiResponse);
            JSONArray errors = resobj.getJSONObject("data").getJSONObject("passwordChange").getJSONArray("errors");
            if (errors.length() == 0) {
                return Response.ok().entity("success").build();
            }
        }
        return Response.ok().entity("").build();
    }

    @POST
    @Path("/updateDetails")
    @Produces({MediaType.APPLICATION_JSON})
    public Response updateUserDetails(String jsonData) {

        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                "MrpCartRequest update user data");
        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                jsonData);

        //Validate params
        Object output = Util.validateParamsInJSON(jsonData,
                Constants.KEY_TOKEN, Constants.KEY_EMAIL, Constants.KEY_MOBILE,
                Constants.KEY_FIRST_NAME, Constants.KEY_LAST_NAME);
        if (output instanceof Error) {

            return Response.ok().entity("").build();
        }

        boolean isProdDB = CommonUtil.checkProdDB(request);
        //Params
        JSONObject obj = new JSONObject(jsonData);
        String token = obj.getString(Constants.KEY_TOKEN);
        String newemail = obj.getString(Constants.KEY_EMAIL);
        String mobile = obj.getString(Constants.KEY_MOBILE);
        String firstName = obj.getString(Constants.KEY_FIRST_NAME);
        String lastName = obj.getString(Constants.KEY_LAST_NAME);
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
            Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
        }

        JSONObject resobj = new JSONObject((String) apiResponse);
        JSONObject data = resobj.getJSONObject("data");
        boolean isValid = true;
        try {
            String me = resobj.getJSONObject("data").getString("me");
            isValid = false;
        } catch (JSONException e) {
        }

        if (isValid) {
            String email = data.getJSONObject("me").getString("email");
            boolean status = ServiceDao.updateUserDetails(newemail, mobile, firstName, lastName, email, isProdDB);
            if (status) {
                return Response.ok().entity("success").build();
            }
        }
        return Response.ok().entity("").build();
    }

    @POST
    @Path("/getMobile")
    @Produces({MediaType.APPLICATION_JSON})
    public Response getMobile(String jsonData) {

        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                "MrpCartRequest update user data");
        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                jsonData);

        //Validate params
        Object output = Util.validateParamsInJSON(jsonData,
                Constants.KEY_EMAIL);
        if (output instanceof Error) {

            return Response.ok().entity("").build();
        }

        boolean isProdDB = CommonUtil.checkProdDB(request);
        //Params
        JSONObject obj = new JSONObject(jsonData);
        String email = obj.getString(Constants.KEY_EMAIL);
        String mobile = ServiceDao.getMobile(email, isProdDB);
        return Response.ok().entity(mobile).build();
    }

    @POST
    @Path("/email/shipmentDetails")
    @Produces({MediaType.APPLICATION_JSON})
    public Response shipmentDetailsEmail(String jsonData) {

        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                "MrpCartRequest shipment details data");
        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                jsonData);

        //Params
        JSONArray arr = new JSONArray(jsonData);
        JSONObject obj = arr.getJSONObject(0);
        JSONObject order = obj.getJSONObject("order");
        String email = order.getString("user_email");
        String id = order.getString("id");

        boolean isProdDB = CommonUtil.checkProdDB(request);
        String resData = "{order(id: \"" + id + "\") {number,fulfillments{lines{id,orderLine{id,productName,thumbnail{url}}}}}}";

        JSONObject obje = new JSONObject();
        obje.put("query", resData);

        ArrayList<BasicNameValuePair> headers = new ArrayList<>();
        headers.add(0, new BasicNameValuePair("Content-Type",
                "application/json"));
        headers.add(0, new BasicNameValuePair("Authorization",
                Constants.HEGMA_API_AUTH_TOKEN));

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
            Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
        }

        JSONObject resobj = new JSONObject((String) apiResponse);
        JSONObject data2 = resobj.getJSONObject("data");

        String fulfilmentId = obj.getString("id");
        String orderNo = data2.getJSONObject("order").getString("number");
        String orderDate = order.getString("created").substring(0, 10);
        String name = order.getJSONObject("shipping_address").getString("first_name") + " " + order.getJSONObject("shipping_address").getString("last_name");
        String address = order.getJSONObject("shipping_address").getString("street_address_1") + ", " + order.getJSONObject("shipping_address").getString("street_address_2");
        String country = order.getJSONObject("shipping_address").getString("city") + ", " + order.getJSONObject("shipping_address").getString("country") + " " + order.getJSONObject("shipping_address").getString("postal_code");
        JSONArray fulfilment = order.getJSONArray("fulfillments");
        String productData = "";
        int totalQuantity = 0;
        double weightedShipping = 0;
        String amount = order.getString("total_gross_amount");
        String[] bccrecipients = {"anuj@qmmtech.com"};
        String[] recipients = {email};
        DecimalFormat df = new DecimalFormat("#0.00");
        try {
            String emailMessage = EmailConstants.EMAIL_HEAD + EmailConstants.EMAIL_ORDER_BODY2
                    + EmailConstants.EMAIL_ORDER_BODY_SUMMARY.replace(Constants.KEY_REPLACE_ORDER_NO, orderNo).replace(Constants.KEY_REPLACE_ORDER_DATE, orderDate).replace(Constants.KEY_REPLACE_ORDER_TOTAL_PRICE, String.valueOf(amount))
                    + EmailConstants.EMAIL_ORDER_BODY_SHIPPING_ADDRESS.replace(Constants.KEY_REPLACE_NAME, name).replace(Constants.KEY_REPLACE_ADDRESS, address).replace(Constants.KEY_REPLACE_COUNTRY, country)
                    + EmailConstants.EMAIL_ORDER_BODY_PRODUCT_HEADER1;

            for (int i = 0; i < fulfilment.length(); i++) {
                JSONObject data1 = fulfilment.getJSONObject(i);
                if (fulfilmentId.equals(data1.getString("id"))) {
                    JSONArray lines = data1.getJSONArray("lines");
                    for (int j = 0; j < lines.length(); j++) {
                        JSONObject data = lines.getJSONObject(j);
                        double amt = Double.parseDouble(data.getString("unit_price_gross"));
                        String wgtShippPrc = null;
                        try {
                            wgtShippPrc = data.getString("weight");
                        } catch (Exception e) {
                        }

                        if (null == wgtShippPrc) {
                        } else {
                            double wgtamt = Double.parseDouble(wgtShippPrc) * data.getInt("quantity");
                            amt = amt - Double.parseDouble(wgtShippPrc);
                            weightedShipping = weightedShipping + wgtamt;
                        }

                        String imageUrl = "";
                        JSONArray fulfilment1 = data2.getJSONObject("order").getJSONArray("fulfillments");
                        for (int k = 0; k < fulfilment1.length(); k++) {
                            JSONObject data3 = fulfilment1.getJSONObject(k);
                            JSONArray lines1 = data3.getJSONArray("lines");
                            for (int l = 0; l < lines1.length(); l++) {
                                JSONObject data4 = lines1.getJSONObject(l);
                                if (data.getString("id").equalsIgnoreCase(data4.getString("id"))) {
                                    imageUrl = data4.getJSONObject("orderLine").getJSONObject("thumbnail").getString("url");
                                }
                            }
                        }
                        productData = productData + EmailConstants.EMAIL_ORDER_BODY_PRODUCT_DATA1.replace(Constants.KEY_REPLACE_PRODUCT_URL, imageUrl).
                                replace(Constants.KEY_REPLACE_PRODUCT_NAME, data.getString("product_name") + " - " + data.getString("variant_name")).replace(Constants.KEY_REPLACE_PRODUCT_QUANTITY, String.valueOf(data.getInt("quantity"))).
                                replace(Constants.KEY_REPLACE_PRODUCT_PRICE, String.valueOf(df.format(amt)));
                        totalQuantity = totalQuantity + data.getInt("quantity");
                    }
                }
            }

            emailMessage = emailMessage + productData;
            emailMessage = emailMessage + EmailConstants.EMAIL_FOOTER;
            SendSmtpMail.sendSSLMessagewithBcc(recipients, bccrecipients, "Your Snapto order " + orderNo + " Shipped", emailMessage);
            ArrayList<HashMap<String, String>> userData = ServiceDao.accountUserEmailData(email, isProdDB);
            String mobile = userData.get(0).get("mobile_no");
            String msg = "Items from your order no. " + orderNo + " have been shipped. It will be delivered shortly. Thank You - Snapto by SNAP ECOMMERCE";
            SMSUtility.SendTransactionalSMSMSG91ROCKET(mobile, msg, Constants.MSG91_SHIPMENT_DLE_ID);
        } catch (MessagingException | UnsupportedEncodingException ex) {
            Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
        }
        return Response.ok().entity("success").build();
    }

    @POST
    @Path("/email/shipmentDelivery")
    @Produces({MediaType.APPLICATION_JSON})
    public Response shipmentDeliveryConfirmationEmail(String jsonData) {

        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                "MrpCartRequest shipment delivery confirmation data");
        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                jsonData);

        //Params
        JSONObject obj = new JSONObject(jsonData);
        String orderNo = obj.getString("order_number");
        String fulfillmentOrder = String.valueOf(obj.getInt("fulfillment_order"));

        boolean isProdDB = CommonUtil.checkProdDB(request);
        String resData = "{orders(first: 10, filter: {search: \"#" + orderNo + "\"}) {edges{node{number,created,userEmail,"
                + "shippingAddress{firstName,lastName,streetAddress1,streetAddress2,city,country{country},postalCode},total{gross{amount}},"
                + "lines{id,quantity,thumbnail{url},unitPrice{gross{amount}},weightedShipping,variantName,productName},"
                + "fulfillments{fulfillmentOrder,lines{id,quantity,orderLine{id,unitPrice{gross{amount}},weightedShipping,variantName}}}}}}}";

        JSONObject obje = new JSONObject();
        obje.put("query", resData);

        ArrayList<BasicNameValuePair> headers = new ArrayList<>();
        headers.add(0, new BasicNameValuePair("Content-Type",
                "application/json"));
        headers.add(0, new BasicNameValuePair("Authorization",
                Constants.HEGMA_API_AUTH_TOKEN));

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
            Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
        }

        JSONObject resobj = new JSONObject((String) apiResponse);
        JSONObject order = resobj.getJSONObject("data").getJSONObject("orders").getJSONArray("edges").getJSONObject(0).getJSONObject("node");
        orderNo = order.getString("number");
        String orderDate = order.getString("created").substring(0, 10);
        String name = order.getJSONObject("shippingAddress").getString("firstName") + " " + order.getJSONObject("shippingAddress").getString("lastName");
        String address = order.getJSONObject("shippingAddress").getString("streetAddress1") + ", " + order.getJSONObject("shippingAddress").getString("streetAddress2");
        String country = order.getJSONObject("shippingAddress").getString("city") + ", " + order.getJSONObject("shippingAddress").getJSONObject("country").getString("country") + " " + order.getJSONObject("shippingAddress").getString("postalCode");
        JSONArray fulfilment = order.getJSONArray("fulfillments");
        String productData = "";
        int totalQuantity = 0;
        double weightedShipping = 0;
        String email = order.getString("userEmail");
        double amount = order.getJSONObject("total").getJSONObject("gross").getDouble("amount");
        String[] bccrecipients = {"anuj@qmmtech.com"};
        String[] recipients = {email};
        DecimalFormat df = new DecimalFormat("#0.00");
        try {
            // Define the desired date format
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd");
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd-MM-yyyy");
            Date date = inputFormat.parse(orderDate);
            orderDate = outputFormat.format(date);
        } catch (Exception e) {
        }
        try {
            String emailMessage = EmailConstants.EMAIL_HEAD + EmailConstants.EMAIL_ORDER_BODY6
                    + EmailConstants.EMAIL_ORDER_BODY_SUMMARY.replace(Constants.KEY_REPLACE_ORDER_NO, orderNo).replace(Constants.KEY_REPLACE_ORDER_DATE, orderDate).replace(Constants.KEY_REPLACE_ORDER_TOTAL_PRICE, String.valueOf(df.format(amount)))
                    + EmailConstants.EMAIL_ORDER_BODY_SHIPPING_ADDRESS.replace(Constants.KEY_REPLACE_NAME, name).replace(Constants.KEY_REPLACE_ADDRESS, address).replace(Constants.KEY_REPLACE_COUNTRY, country)
                    + EmailConstants.EMAIL_ORDER_BODY_PRODUCT_HEADER4;

            for (int i = 0; i < fulfilment.length(); i++) {
                JSONObject data1 = fulfilment.getJSONObject(i);
                if (fulfillmentOrder.equals(String.valueOf(data1.getInt("fulfillmentOrder")))) {
                    JSONArray lines = data1.getJSONArray("lines");
                    for (int j = 0; j < lines.length(); j++) {
                        JSONObject data = lines.getJSONObject(j);
                        double amt = data.getJSONObject("orderLine").getJSONObject("unitPrice").getJSONObject("gross").getDouble("amount");
                        String wgtShippPrc = null;
                        try {
                            wgtShippPrc = data.getString("weightedShipping");
                        } catch (Exception e) {
                        }

                        if (null == wgtShippPrc) {
                        } else {
                            double wgtamt = Double.parseDouble(wgtShippPrc) * data.getInt("quantity");
                            amt = amt - Double.parseDouble(wgtShippPrc);
                            weightedShipping = weightedShipping + wgtamt;
                        }

                        String imageUrl = "";
                        JSONArray lines1 = order.getJSONArray("lines");
                        for (int l = 0; l < lines1.length(); l++) {
                            JSONObject data4 = lines1.getJSONObject(l);
                            if (data.getJSONObject("orderLine").getString("id").equalsIgnoreCase(data4.getString("id"))) {
                                imageUrl = data4.getJSONObject("thumbnail").getString("url");
                            }
                        }
                        productData = productData + EmailConstants.EMAIL_ORDER_BODY_PRODUCT_DATA1.replace(Constants.KEY_REPLACE_PRODUCT_URL, imageUrl).
                                replace(Constants.KEY_REPLACE_PRODUCT_NAME, data.getJSONObject("orderLine").getString("productName") + " - " + data.getJSONObject("orderLine").getString("variantName")).replace(Constants.KEY_REPLACE_PRODUCT_QUANTITY, String.valueOf(data.getInt("quantity"))).
                                replace(Constants.KEY_REPLACE_PRODUCT_PRICE, String.valueOf(df.format(amt)));
                        totalQuantity = totalQuantity + data.getInt("quantity");
                    }
                }
            }

            ArrayList<HashMap<String, String>> userData = ServiceDao.accountUserEmailData(email, isProdDB);
            String mobile = userData.get(0).get("mobile_no");
            String msg = "Your order " + orderNo + " has been delivered. Thank you for shopping with us - Snapto by SNAP ECOMMERCE";
            SMSUtility.SendTransactionalSMSMSG91ROCKET(mobile, msg, Constants.MSG91_DELIVERY_DLE_ID);
            emailMessage = emailMessage + productData;
            emailMessage = emailMessage + EmailConstants.EMAIL_FOOTER;
            SendSmtpMail.sendSSLMessagewithBcc(recipients, bccrecipients, "Your Snapto order " + orderNo + " Delivered", emailMessage);
        } catch (MessagingException | UnsupportedEncodingException ex) {
            Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
        }

        return Response.ok().entity("success").build();
    }

    @POST
    @Path("/email/orderCancellation")
    @Produces({MediaType.APPLICATION_JSON})
    public Response orderCancellationConfirmationEmail(String jsonData) {

        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                "MrpCartRequest order cancellation confirmation data");
        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                jsonData);

        //Params
        JSONArray arr = new JSONArray(jsonData);
        JSONObject order = arr.getJSONObject(0);
        String email = order.getString("user_email");
        String id = order.getString("id");

        boolean isProdDB = CommonUtil.checkProdDB(request);
        String resData = "{order(id: \"" + id + "\") {number,lines{id,thumbnail{url}}}}";

        JSONObject obje = new JSONObject();
        obje.put("query", resData);

        ArrayList<BasicNameValuePair> headers = new ArrayList<>();
        headers.add(0, new BasicNameValuePair("Content-Type",
                "application/json"));
        headers.add(0, new BasicNameValuePair("Authorization",
                Constants.HEGMA_API_AUTH_TOKEN));

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
            Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
        }

        JSONObject resobj = new JSONObject((String) apiResponse);
        JSONObject data1 = resobj.getJSONObject("data");

        String orderNo = data1.getJSONObject("order").getString("number");
        String orderDate = order.getString("created").substring(0, 10);
        String name = order.getJSONObject("shipping_address").getString("first_name") + " " + order.getJSONObject("shipping_address").getString("last_name");
        String address = order.getJSONObject("shipping_address").getString("street_address_1") + ", " + order.getJSONObject("shipping_address").getString("street_address_2");
        String country = order.getJSONObject("shipping_address").getString("city") + ", " + order.getJSONObject("shipping_address").getString("country") + " " + order.getJSONObject("shipping_address").getString("postal_code");
        JSONArray lines = order.getJSONArray("lines");
        String productData = "";
        int totalQuantity = 0;
        double weightedShipping = 0;
        String amount = order.getString("total_gross_amount");
        String[] bccrecipients = {"anuj@qmmtech.com"};
        String[] recipients = {email};
        DecimalFormat df = new DecimalFormat("#0.00");
        try {
            String emailMessage = EmailConstants.EMAIL_HEAD + EmailConstants.EMAIL_ORDER_BODY4
                    + EmailConstants.EMAIL_ORDER_BODY_SUMMARY.replace(Constants.KEY_REPLACE_ORDER_NO, orderNo).replace(Constants.KEY_REPLACE_ORDER_DATE, orderDate).replace(Constants.KEY_REPLACE_ORDER_TOTAL_PRICE, String.valueOf(amount))
                    + EmailConstants.EMAIL_ORDER_BODY_SHIPPING_ADDRESS.replace(Constants.KEY_REPLACE_NAME, name).replace(Constants.KEY_REPLACE_ADDRESS, address).replace(Constants.KEY_REPLACE_COUNTRY, country)
                    + EmailConstants.EMAIL_ORDER_BODY_PRODUCT_HEADER2;

            for (int i = 0; i < lines.length(); i++) {
                JSONObject data = lines.getJSONObject(i);
                double amt = Double.parseDouble(data.getString("unit_price_gross_amount"));
                String wgtShippPrc = null;
                try {
                    wgtShippPrc = data.getString("weight");
                } catch (Exception e) {
                }

                if (null == wgtShippPrc) {
                } else {
                    double wgtamt = Double.parseDouble(wgtShippPrc) * data.getInt("quantity");
                    amt = amt - Double.parseDouble(wgtShippPrc);
                    weightedShipping = weightedShipping + wgtamt;
                }

                String imageUrl = "";
                JSONArray lines1 = data1.getJSONObject("order").getJSONArray("lines");
                for (int j = 0; j < lines1.length(); j++) {
                    JSONObject data2 = lines1.getJSONObject(j);
                    if (data.getString("id").equalsIgnoreCase(data2.getString("id"))) {
                        imageUrl = data2.getJSONObject("thumbnail").getString("url");
                    }
                }

                productData = productData + EmailConstants.EMAIL_ORDER_BODY_PRODUCT_DATA1.replace(Constants.KEY_REPLACE_PRODUCT_URL, imageUrl).
                        replace(Constants.KEY_REPLACE_PRODUCT_NAME, data.getString("variant_name")).replace(Constants.KEY_REPLACE_PRODUCT_QUANTITY, String.valueOf(data.getInt("quantity"))).
                        replace(Constants.KEY_REPLACE_PRODUCT_PRICE, String.valueOf(df.format(amt)));
                totalQuantity = totalQuantity + data.getInt("quantity");
            }

            emailMessage = emailMessage + productData;
            emailMessage = emailMessage + EmailConstants.EMAIL_FOOTER;
            SendSmtpMail.sendSSLMessagewithBcc(recipients, bccrecipients, "Your Snapto order " + orderNo + " Cancelled", emailMessage);
        } catch (MessagingException | UnsupportedEncodingException ex) {
            Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
        }
        return Response.ok().entity("success").build();
    }

    @POST
    @Path("/email/orderReplace")
    @Produces({MediaType.APPLICATION_JSON})
    public Response orderReplaceConfirmEmail(String jsonData) {

        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                "MrpCartRequest order replace data");
        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                jsonData);

        //Params
        JSONArray arr = new JSONArray(jsonData);
        JSONObject order = arr.getJSONObject(0);
        String email = order.getString("user_email");
        String id = order.getString("id");

        boolean isProdDB = CommonUtil.checkProdDB(request);
        String resData = "{order(id: \"" + id + "\") {number,fulfillments{lines{id,orderLine{id,productName,thumbnail{url}}}}}}";

        JSONObject obje = new JSONObject();
        obje.put("query", resData);

        ArrayList<BasicNameValuePair> headers = new ArrayList<>();
        headers.add(0, new BasicNameValuePair("Content-Type",
                "application/json"));
        headers.add(0, new BasicNameValuePair("Authorization",
                Constants.HEGMA_API_AUTH_TOKEN));

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
            Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
        }

        JSONObject resobj = new JSONObject((String) apiResponse);
        JSONObject data2 = resobj.getJSONObject("data");

        String orderNo = data2.getJSONObject("order").getString("number");
        String orderDate = order.getString("created").substring(0, 10);
        String name = order.getJSONObject("shipping_address").getString("first_name") + " " + order.getJSONObject("shipping_address").getString("last_name");
        String address = order.getJSONObject("shipping_address").getString("street_address_1") + ", " + order.getJSONObject("shipping_address").getString("street_address_2");
        String country = order.getJSONObject("shipping_address").getString("city") + ", " + order.getJSONObject("shipping_address").getString("country") + " " + order.getJSONObject("shipping_address").getString("postal_code");
        JSONArray fulfilment = order.getJSONArray("fulfillments");
        String productData = "";
        int totalQuantity = 0;
        double weightedShipping = 0;
        String amount = order.getString("total_gross_amount");
        String[] bccrecipients = {"anuj@qmmtech.com"};
        String[] recipients = {email};
        DecimalFormat df = new DecimalFormat("#0.00");
        boolean status = false;
        try {
            String emailMessage = EmailConstants.EMAIL_HEAD + EmailConstants.EMAIL_ORDER_BODY7
                    + EmailConstants.EMAIL_ORDER_BODY_SUMMARY.replace(Constants.KEY_REPLACE_ORDER_NO, orderNo).replace(Constants.KEY_REPLACE_ORDER_DATE, orderDate).replace(Constants.KEY_REPLACE_ORDER_TOTAL_PRICE, String.valueOf(amount))
                    + EmailConstants.EMAIL_ORDER_BODY_SHIPPING_ADDRESS.replace(Constants.KEY_REPLACE_NAME, name).replace(Constants.KEY_REPLACE_ADDRESS, address).replace(Constants.KEY_REPLACE_COUNTRY, country)
                    + EmailConstants.EMAIL_ORDER_BODY_PRODUCT_HEADER5;

            for (int i = 0; i < fulfilment.length(); i++) {
                JSONObject data1 = fulfilment.getJSONObject(i);
                Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                        "MrpCartRequest order replace status data");
                Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                        data1.getString("status"));
                if (data1.getString("status").equals("replaced") || data1.getString("status").equals("refunded_and_returned")
                        || data1.getString("status").equals("returned")) {
                    String fulfTime = data1.getString("created");
                    long seconds = 10;
                    String productStatus = "Returned";
                    if (data1.getString("status").equals("replaced")) {
                        productStatus = "Replaced";
                    }
                    try {
                        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                        Date d1 = formatter.parse(fulfTime);
                        Date d2 = formatter.parse(Instant.now().toString());
                        seconds = (d2.getTime() - d1.getTime()) / 1000;
                    } catch (Exception e) {
                    }
                    Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                            "MrpCartRequest order replace time data");
                    Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                            String.valueOf(seconds));
                    if (seconds <= 30) {
                        status = true;
                        JSONArray lines = data1.getJSONArray("lines");
                        for (int j = 0; j < lines.length(); j++) {
                            JSONObject data = lines.getJSONObject(j);
                            double amt = Double.parseDouble(data.getString("unit_price_gross"));
                            String wgtShippPrc = null;
                            try {
                                wgtShippPrc = data.getString("weight");
                            } catch (Exception e) {
                            }

                            if (null == wgtShippPrc) {
                            } else {
                                double wgtamt = Double.parseDouble(wgtShippPrc) * data.getInt("quantity");
                                amt = amt - Double.parseDouble(wgtShippPrc);
                                weightedShipping = weightedShipping + wgtamt;
                            }

                            String imageUrl = "";
                            JSONArray fulfilment1 = data2.getJSONObject("order").getJSONArray("fulfillments");
                            for (int k = 0; k < fulfilment1.length(); k++) {
                                JSONObject data3 = fulfilment1.getJSONObject(k);
                                JSONArray lines1 = data3.getJSONArray("lines");
                                for (int l = 0; l < lines1.length(); l++) {
                                    JSONObject data4 = lines1.getJSONObject(l);
                                    if (data.getString("id").equalsIgnoreCase(data4.getString("id"))) {
                                        imageUrl = data4.getJSONObject("orderLine").getJSONObject("thumbnail").getString("url");
                                    }
                                }
                            }
                            productData = productData + EmailConstants.EMAIL_ORDER_BODY_PRODUCT_DATA2.replace(Constants.KEY_REPLACE_PRODUCT_URL, imageUrl).
                                    replace(Constants.KEY_REPLACE_PRODUCT_NAME, data.getString("variant_name")).replace(Constants.KEY_REPLACE_PRODUCT_QUANTITY, String.valueOf(data.getInt("quantity"))).
                                    replace(Constants.KEY_REPLACE_PRODUCT_PRICE, String.valueOf(df.format(amt))).replace(Constants.KEY_REPLACE_PRODUCT_STATUS, productStatus);
                            totalQuantity = totalQuantity + data.getInt("quantity");
                        }
                    }
                }
            }

            emailMessage = emailMessage + productData;
            emailMessage = emailMessage + EmailConstants.EMAIL_FOOTER;
            if (status) {
                Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                        "MrpCartRequest order replace correct data");
                Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                        "yes");
                SendSmtpMail.sendSSLMessagewithBcc(recipients, bccrecipients, "Your Snapto order " + orderNo + " Returned/Replaced", emailMessage);
            }
        } catch (MessagingException | UnsupportedEncodingException ex) {
            Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
        }
        return Response.ok().entity("success").build();
    }

    @POST
    @Path("/email/paymentConfirmation")
    @Produces({MediaType.APPLICATION_JSON})
    public Response codPaymentConfirmationEmail(String jsonData) {

        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                "MrpCartRequest cod payment confirmation data");
        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                jsonData);

        //Params
        JSONArray arr = new JSONArray(jsonData);
        JSONObject order = arr.getJSONObject(0);
        String email = order.getString("user_email");
        String id = order.getString("id");

        boolean isProdDB = CommonUtil.checkProdDB(request);
        String resData = "{order(id: \"" + id + "\") {number,lines{id,thumbnail{url}}}}";

        JSONObject obje = new JSONObject();
        obje.put("query", resData);

        ArrayList<BasicNameValuePair> headers = new ArrayList<>();
        headers.add(0, new BasicNameValuePair("Content-Type",
                "application/json"));
        headers.add(0, new BasicNameValuePair("Authorization",
                Constants.HEGMA_API_AUTH_TOKEN));

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
            Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
        }

        JSONObject resobj = new JSONObject((String) apiResponse);
        JSONObject data1 = resobj.getJSONObject("data");

        String orderNo = data1.getJSONObject("order").getString("number");
        String orderDate = order.getString("created").substring(0, 10);
        String name = order.getJSONObject("shipping_address").getString("first_name") + " " + order.getJSONObject("shipping_address").getString("last_name");
        String address = order.getJSONObject("shipping_address").getString("street_address_1") + ", " + order.getJSONObject("shipping_address").getString("street_address_2");
        String country = order.getJSONObject("shipping_address").getString("city") + ", " + order.getJSONObject("shipping_address").getString("country") + " " + order.getJSONObject("shipping_address").getString("postal_code");
        JSONArray lines = order.getJSONArray("lines");
        String productData = "";
        int totalQuantity = 0;
        double weightedShipping = 0;
        String amount = order.getString("total_gross_amount");
        String[] bccrecipients = {"anuj@qmmtech.com"};
        String[] recipients = {email};
        DecimalFormat df = new DecimalFormat("#0.00");
        try {
            String emailMessage = EmailConstants.EMAIL_HEAD + EmailConstants.EMAIL_ORDER_BODY5
                    + EmailConstants.EMAIL_ORDER_BODY_SUMMARY.replace(Constants.KEY_REPLACE_ORDER_NO, orderNo).replace(Constants.KEY_REPLACE_ORDER_DATE, orderDate).replace(Constants.KEY_REPLACE_ORDER_TOTAL_PRICE, String.valueOf(amount))
                    + EmailConstants.EMAIL_ORDER_BODY_SHIPPING_ADDRESS.replace(Constants.KEY_REPLACE_NAME, name).replace(Constants.KEY_REPLACE_ADDRESS, address).replace(Constants.KEY_REPLACE_COUNTRY, country)
                    + EmailConstants.EMAIL_ORDER_BODY_PRODUCT_HEADER3;

            for (int i = 0; i < lines.length(); i++) {
                JSONObject data = lines.getJSONObject(i);
                double amt = Double.parseDouble(data.getString("unit_price_gross_amount"));
                String wgtShippPrc = null;
                try {
                    wgtShippPrc = data.getString("weight");
                } catch (Exception e) {
                }

                if (null == wgtShippPrc) {
                } else {
                    double wgtamt = Double.parseDouble(wgtShippPrc) * data.getInt("quantity");
                    amt = amt - Double.parseDouble(wgtShippPrc);
                    weightedShipping = weightedShipping + wgtamt;
                }

                String imageUrl = "";
                JSONArray lines1 = data1.getJSONObject("order").getJSONArray("lines");
                for (int j = 0; j < lines1.length(); j++) {
                    JSONObject data2 = lines1.getJSONObject(j);
                    if (data.getString("id").equalsIgnoreCase(data2.getString("id"))) {
                        imageUrl = data2.getJSONObject("thumbnail").getString("url");
                    }
                }

                productData = productData + EmailConstants.EMAIL_ORDER_BODY_PRODUCT_DATA1.replace(Constants.KEY_REPLACE_PRODUCT_URL, imageUrl).
                        replace(Constants.KEY_REPLACE_PRODUCT_NAME, data.getString("variant_name")).replace(Constants.KEY_REPLACE_PRODUCT_QUANTITY, String.valueOf(data.getInt("quantity"))).
                        replace(Constants.KEY_REPLACE_PRODUCT_PRICE, String.valueOf(df.format(amt)));
                totalQuantity = totalQuantity + data.getInt("quantity");
            }

            emailMessage = emailMessage + productData;
            emailMessage = emailMessage + EmailConstants.EMAIL_FOOTER;
            SendSmtpMail.sendSSLMessagewithBcc(recipients, bccrecipients, "Your Snapto order " + orderNo + " Payment Successful", emailMessage);
        } catch (MessagingException | UnsupportedEncodingException ex) {
            Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
        }
        return Response.ok().entity("success").build();
    }

    @POST
    @Path("/getCheckoutToken")
    @Produces({MediaType.APPLICATION_JSON})
    public Response getCheckoutToken(String jsonData) {

        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                "MrpCartRequest get checkout token data");
        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                jsonData);

        //Validate params
        Object output = Util.validateParamsInJSON(jsonData,
                Constants.KEY_TOKEN);
        if (output instanceof Error) {

            return Response.ok().entity("").build();
        }

        boolean isProdDB = CommonUtil.checkProdDB(request);
        //Params
        JSONObject obj = new JSONObject(jsonData);
        String token = obj.getString(Constants.KEY_TOKEN);
        String email = CommonUtil.userVerify(token, isProdDB);
        if (email.isEmpty()) {
            return Response.ok().entity("").build();
        }

        String resData = "{checkouts(first: 1,  filter: {search: \"" + email + "\"}, sortBy: {direction: DESC, field: CREATION_DATE}){edges{node{token}}}}";

        JSONObject obje = new JSONObject();
        obje.put("query", resData);

        ArrayList<BasicNameValuePair> headers = new ArrayList<>();
        headers.add(0, new BasicNameValuePair("Authorization",
                Constants.HEGMA_API_AUTH_TOKEN));
        headers.add(0, new BasicNameValuePair("Content-Type",
                "application/json"));

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
            Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
        }

        JSONObject resobj = new JSONObject((String) apiResponse);
        JSONArray arr = resobj.getJSONObject("data").getJSONObject("checkouts").getJSONArray("edges");
        String checkoutToken = "";
        if (arr.length() > 0) {
            checkoutToken = arr.getJSONObject(0).getJSONObject("node").getString("token");
        }

        return Response.ok().entity(checkoutToken).build();
    }

    @POST
    @Path("/saveSearchTerms")
    @Produces({MediaType.APPLICATION_JSON})
    public Response saveSearchTerms(String jsonData) {

        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                "MrpCartRequest save search term data");
        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                jsonData);

        //Validate params
        Object output = Util.validateParamsInJSON(jsonData,
                Constants.KEY_SEARCH_TERM);
        if (output instanceof Error) {

            return Response.ok().entity("").build();
        }

        boolean isProdDB = CommonUtil.checkProdDB(request);
        //Params
        JSONObject obj = new JSONObject(jsonData);
        String email = obj.getString(Constants.KEY_EMAIL);
        String searchTerm = obj.getString(Constants.KEY_SEARCH_TERM);

        String resData = "{products(channel: \"default-channel\", filter: {search: \"" + searchTerm + "\"}) {totalCount}}";

        JSONObject obje = new JSONObject();
        obje.put("query", resData);

        ArrayList<BasicNameValuePair> headers = new ArrayList<>();
        headers.add(0, new BasicNameValuePair("Content-Type",
                "application/json"));

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
            Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
        }

        JSONObject resobj = new JSONObject((String) apiResponse);
        int totalCount = resobj.getJSONObject("data").getJSONObject("products").getInt("totalCount");
        boolean status = ServiceDao.insertSearchTerm(searchTerm, email, String.valueOf(totalCount), isProdDB);
        return Response.ok().entity(status).build();
    }

    @POST
    @Path("/orderReview")
    @Produces({MediaType.APPLICATION_JSON})
    public Response orderReview(String jsonData) {
        //Validate params
        Object output = Util.validateParamsInJSON(jsonData, Constants.KEY_ORDER_ID, Constants.KEY_RATING, Constants.KEY_REVIEW,
                Constants.KEY_PRODUCT_NAME);
        if (output instanceof Error) {

            return Response.ok().entity("").build();
        }

        //Params
        JSONObject obj = new JSONObject(jsonData);
        String token = obj.getString(Constants.KEY_TOKEN);
        String orderId = obj.getString(Constants.KEY_ORDER_ID);
        int rating = obj.getInt(Constants.KEY_RATING);
        String review = obj.getString(Constants.KEY_REVIEW);
        String productName = obj.getString(Constants.KEY_PRODUCT_NAME);

        boolean isProdDB = CommonUtil.checkProdDB(request);
        String userEmail = CommonUtil.userVerify(token, isProdDB);
        if (!userEmail.isEmpty()) {
            ArrayList<BasicNameValuePair> headers = new ArrayList<>();
            headers.add(0, new BasicNameValuePair("Content-Type",
                    "application/json"));
            headers.add(0, new BasicNameValuePair("Authorization", Constants.HEGMA_API_AUTH_TOKEN));
            String resData = null;
            String url;
            if (isProdDB) {
                url = Constants.HEGMA_API_URL;
            } else {
                url = Constants.HEGMA_BETA_API_URL;
            }
            String astrologerData = "{order(id:\"" + orderId + "\"){userEmail number}}";
            JSONObject obje = new JSONObject();
            obje.put("query", astrologerData);
            try {
                resData = HTTPUtil.executeUrl(new URL(url), obje.toString(), headers, Constants.HTTP_REQUEST.POST.name());
            } catch (MalformedURLException ex) {
                Logger.getLogger(CommonUtil.class.getName()).log(Level.SEVERE, null, ex);
            }
            JSONObject resobj1 = new JSONObject((String) resData);
            String email = resobj1.getJSONObject("data").getJSONObject("order").getString("userEmail");
            String orderNumber = resobj1.getJSONObject("data").getJSONObject("order").getString("number");
            if (email.equals(userEmail)) {
                ServiceDao.updateOrderRating(Integer.parseInt(orderNumber), rating, review, productName, isProdDB);
                double avgRating = ServiceDao.avgProductRating(Integer.parseInt(orderNumber), isProdDB);
                ServiceDao.updateProductRating(Integer.parseInt(orderNumber), avgRating, isProdDB);
                return Response.ok().entity("Product Rating Is updated").build();
            } else {
                return Response.ok().entity("This Order is Not Yours").build();
            }
        } else {
            return Response.ok().entity("User Not Found").build();
        }
    }

    @POST
    @Path("/order-cancellation-request")
    @Produces({MediaType.APPLICATION_JSON})
    public Response orderCancellationRequest(String jsonData) {
        //Validate params
        Object output = Util.validateParamsInJSON(jsonData, Constants.KEY_TOKEN, Constants.KEY_ORDER_ID,
                Constants.KEY_REMARK);
        if (output instanceof Error) {

            return Response.ok().entity("").build();
        }

        //Params
        JSONObject obj = new JSONObject(jsonData);
        String token = obj.getString(Constants.KEY_TOKEN);
        String orderId = obj.getString(Constants.KEY_ORDER_ID);
        String remark = obj.getString(Constants.KEY_REMARK);

        boolean isProdDB = CommonUtil.checkProdDB(request);
        String userEmail = CommonUtil.userVerify(token, isProdDB);
        if (!userEmail.isEmpty()) {
            ArrayList<BasicNameValuePair> headers = new ArrayList<>();
            headers.add(0, new BasicNameValuePair("Content-Type",
                    "application/json"));
            headers.add(0, new BasicNameValuePair("Authorization", Constants.HEGMA_API_AUTH_TOKEN));
            String resData = null;
            String url;
            if (isProdDB) {
                url = Constants.HEGMA_API_URL;
            } else {
                url = Constants.HEGMA_BETA_API_URL;
            }
            String astrologerData = "{order(id:\"" + orderId + "\"){userEmail number}}";
            JSONObject obje = new JSONObject();
            obje.put("query", astrologerData);
            try {
                resData = HTTPUtil.executeUrl(new URL(url), obje.toString(), headers, Constants.HTTP_REQUEST.POST.name());
            } catch (MalformedURLException ex) {
                Logger.getLogger(CommonUtil.class.getName()).log(Level.SEVERE, null, ex);
            }
            JSONObject resobj1 = new JSONObject((String) resData);
            String email = resobj1.getJSONObject("data").getJSONObject("order").getString("userEmail");
            String orderNumber = resobj1.getJSONObject("data").getJSONObject("order").getString("number");
            if (email.equals(userEmail)) {
                String[] bccrecipients = {"anuj@qmmtech.com"};
                String[] recipients = {"care.mrpc@gmail.com"};
                try {
                    String emailMessage = EmailConstants.EMAIL_HEAD
                            + EmailConstants.EMAIL_CANCELLATION_BODY.replace(Constants.KEY_REPLACE_ORDER_NO, orderNumber).replace(Constants.KEY_REPLACE_REMARK, remark)
                            + EmailConstants.EMAIL_FOOTER;
                    SendSmtpMail.sendSSLMessagewithBcc(recipients, bccrecipients, "Order Cancellation Request for order number " + orderNumber, emailMessage);
                } catch (MessagingException | UnsupportedEncodingException ex) {
                    Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
                }
                return Response.ok().entity("Mail Successfully send").build();
            } else {
                return Response.ok().entity("This Order is Not Yours").build();
            }
        } else {
            return Response.ok().entity("User Not Found").build();
        }
    }

    @POST
    @Path("/updateOrder")
    @Produces({MediaType.APPLICATION_JSON})
    public Response updateOrder(String jsonData) {

        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                "MrpCartRequest update order data");
        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                jsonData);

        //Validate params
        Object output = Util.validateParamsInJSON(jsonData,
                Constants.KEY_TOKEN, Constants.KEY_STATUS, Constants.KEY_ORDER_ID);
        if (output instanceof Error) {

            return Response.ok().entity("").build();
        }

        boolean isProdDB = CommonUtil.checkProdDB(request);
        //Params
        JSONObject obj = new JSONObject(jsonData);
        String token = obj.getString(Constants.KEY_TOKEN);
        String status = obj.getString(Constants.KEY_STATUS);
        String orderId = obj.getString(Constants.KEY_ORDER_ID);
        ServiceDao.updateOrderStatus(orderId, isProdDB);
//        String resData = "{me{id,email}}";
//
//        JSONObject obje = new JSONObject();
//        obje.put("query", resData);
//
//        ArrayList<BasicNameValuePair> headers = new ArrayList<>();
//        headers.add(0, new BasicNameValuePair("Content-Type",
//                "application/json"));
//        headers.add(0, new BasicNameValuePair("Authorization",
//                "JWT " + token));
//
//        String url;
//        if (isProdDB) {
//            url = Constants.HEGMA_API_URL;
//        } else {
//            url = Constants.HEGMA_BETA_API_URL;
//        }
//        String apiResponse = null;
//        try {
//            apiResponse = HTTPUtil.executeUrl(new URL(url), obje.toString(), headers, Constants.HTTP_REQUEST.POST.name());
//        } catch (MalformedURLException ex) {
//            Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
//        }
//
//        JSONObject resobj = new JSONObject((String) apiResponse);
//        JSONObject data = resobj.getJSONObject("data");
//        boolean isValid = true;
//        try {
//            String me = resobj.getJSONObject("data").getString("me");
//            isValid = false;
//        } catch (JSONException e) {
//        }
//
//        if (isValid) {
//            data.getJSONObject("me").getString("email");
//            boolean status1 = ServiceDao.updateOrderStatus(orderId, isProdDB);
//            if (status1) {
//                return Response.ok().entity("success").build();
//            }
//        }
        return Response.ok().entity("").build();
    }

    @POST
    @Path("/updateLineOrder")
    @Produces({MediaType.APPLICATION_JSON})
    public Response updateLineOrder(String jsonData) {

        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                "MrpCartRequest update line order data");
        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                jsonData);

        //Validate params
        Object output = Util.validateParamsInJSON(jsonData,
                Constants.KEY_TOKEN, Constants.KEY_STATUS,
                Constants.KEY_ORDER_ID, Constants.KEY_ORDER_LINE_ID);
        if (output instanceof Error) {

            return Response.ok().entity("").build();
        }

        boolean isProdDB = CommonUtil.checkProdDB(request);
        //Params
        JSONObject obj = new JSONObject(jsonData);
        String token = obj.getString(Constants.KEY_TOKEN);
        String status = obj.getString(Constants.KEY_STATUS);
        String orderId = obj.getString(Constants.KEY_ORDER_ID);
        String orderLineId = obj.getString(Constants.KEY_ORDER_LINE_ID);
        ServiceDao.updateOrderLineStatus(orderId, orderLineId, isProdDB);
//        String resData = "{me{id,email}}";
//
//        JSONObject obje = new JSONObject();
//        obje.put("query", resData);
//
//        ArrayList<BasicNameValuePair> headers = new ArrayList<>();
//        headers.add(0, new BasicNameValuePair("Content-Type",
//                "application/json"));
//        headers.add(0, new BasicNameValuePair("Authorization",
//                "JWT " + token));
//
//        String url;
//        if (isProdDB) {
//            url = Constants.HEGMA_API_URL;
//        } else {
//            url = Constants.HEGMA_BETA_API_URL;
//        }
//        String apiResponse = null;
//        try {
//            apiResponse = HTTPUtil.executeUrl(new URL(url), obje.toString(), headers, Constants.HTTP_REQUEST.POST.name());
//        } catch (MalformedURLException ex) {
//            Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
//        }
//
//        JSONObject resobj = new JSONObject((String) apiResponse);
//        JSONObject data = resobj.getJSONObject("data");
//        boolean isValid = true;
//        try {
//            String me = resobj.getJSONObject("data").getString("me");
//            isValid = false;
//        } catch (JSONException e) {
//        }
//
//        if (isValid) {
//            data.getJSONObject("me").getString("email");
//            boolean status1 = ServiceDao.updateOrderLineStatus(orderId, orderLineId, isProdDB);
//            if (status1) {
//                return Response.ok().entity("success").build();
//            }
//        }
        return Response.ok().entity("").build();
    }

    @GET
    @Path("/testingUrl")
    @Produces({MediaType.APPLICATION_JSON})
    public Response testingUrl() {

        String message = "";
        return Response.ok().entity(message).build();
    }

    @POST
    @Path("/accountDeActivate")
    @Produces({MediaType.APPLICATION_JSON})
    public Response accountDeActivate(String jsonData) {

        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                "MrpCartRequest account deactivate data");
        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE,
                jsonData);

        //Validate params
        Object output = Util.validateParamsInJSON(jsonData,
                Constants.KEY_TOKEN);
        if (output instanceof Error) {

            return Response.ok().entity("").build();
        }

        boolean isProdDB = CommonUtil.checkProdDB(request);
        //Params
        JSONObject obj = new JSONObject(jsonData);
        String token = obj.getString(Constants.KEY_TOKEN);
        String userEmail = CommonUtil.userVerify(token, isProdDB);
        if (!userEmail.isEmpty()) {
            String[] recipients = {"info@snapto.in"};
            try {
                String subject = "Customer account delete request recieved from " + userEmail;
                String body = "Customer account delete request recieved from " + userEmail;
                SendSmtpMail.sendSSLMessage(recipients, subject, body);
            } catch (MessagingException | UnsupportedEncodingException ex) {
                Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return Response.ok().entity("").build();
    }
    private void sendAdminNotification(String orderNo, String customerEmail, String customerName, 
                                   String mobile, double amount, String deliveryDate, 
                                   String slot, String paymentType) {
    try {
        String[] adminRecipients = {ADMIN_EMAIL};
        String subject = "New Order #" + orderNo + " from " + customerEmail;
        String body = "<h3>New Order Placed</h3>"
                    + "<b>Order Number:</b> " + orderNo + "<br>"
                    + "<b>Customer:</b> " + customerName + " (" + customerEmail + ", " + mobile + ")<br>"
                    + "<b>Total Amount:</b> Rs " + String.format("%.2f", amount) + "<br>"
                    + "<b>Delivery Date:</b> " + deliveryDate + "<br>"
                    + "<b>Time Slot:</b> " + slot + "<br>"
                    + "<b>Payment Type:</b> " + paymentType + "<br>"
                    + "<b>Please check the admin dashboard for details.</b>";
        SendSmtpMail.sendSSLMessage(adminRecipients, subject, body);
    } catch (Exception e) {
        Logger.getLogger(DmartService.class.getName()).log(Level.SEVERE, 
                "Failed to send admin notification for order " + orderNo, e);
    }
}

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        System.out.println("CORSFilter HTTP Request: " + request.getMethod());

        // Authorize (allow) all domains to consume the content
        ((HttpServletResponse) servletResponse).addHeader("Access-Control-Allow-Origin", "*");
        ((HttpServletResponse) servletResponse).addHeader("Access-Control-Allow-Methods", "OPTIONS,POST");
        ((HttpServletResponse) servletResponse).addHeader("Access-Control-Allow-Headers", "*");
        HttpServletResponse resp = (HttpServletResponse) servletResponse;

        // pass the request along the filter chain
        chain.doFilter(request, servletResponse);
    }

    @Override
    public void destroy() {

    }

}
