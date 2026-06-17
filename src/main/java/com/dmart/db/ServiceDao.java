
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.dmart.db;

import com.dmart.model.Constants;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author Rahul
 */
public class ServiceDao {
    
    // Simple in-memory cache with TTL
    private static final ConcurrentHashMap<String, CacheEntry> queryCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MINUTES = 30; // Cache entries expire after 30 minutes
    
    // Cache entry class to store value and timestamp
    private static class CacheEntry {
        private final Object value;
        private final long timestamp;
        
        public CacheEntry(Object value) {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > TimeUnit.MINUTES.toMillis(CACHE_TTL_MINUTES);
        }
        
        public Object getValue() {
            return value;
        }
    }
    
    // Helper method to generate cache key
    private static String generateCacheKey(String methodName, Object... params) {
        StringBuilder key = new StringBuilder(methodName);
        for (Object param : params) {
            key.append(":").append(param != null ? param.toString() : "null");
        }
        return key.toString();
    }
    
    // Helper method to get from cache
    @SuppressWarnings("unchecked")
    private static <T> T getFromCache(String cacheKey) {
        CacheEntry entry = queryCache.get(cacheKey);
        if (entry != null && !entry.isExpired()) {
            return (T) entry.getValue();
        } else if (entry != null) {
            queryCache.remove(cacheKey);
        }
        return null;
    }
    
    // Helper method to put in cache
    private static void putInCache(String cacheKey, Object value) {
        if (value != null) {
            queryCache.put(cacheKey, new CacheEntry(value));
        }
    }
    
    // Helper method to clear cache
    public static void clearCache() {
        queryCache.clear();
    }
    
    // Helper method to clear cache for specific pattern
    public static void clearCacheByPrefix(String prefix) {
        queryCache.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public static ArrayList<HashMap<String, String>> verifyOtp(String mobile, String otp, boolean isProdDB) {
        String cacheKey = generateCacheKey("verifyOtp", mobile, otp, isProdDB);
        
        // Try to get from cache
        ArrayList<HashMap<String, String>> cachedResult = getFromCache(cacheKey);
        if (cachedResult != null) {
            return new ArrayList<>(cachedResult); // Return a copy to avoid mutation
        }
        
        // Get status and value from DB
        ArrayList<HashMap<String, String>> result = null;
        try {
            result = DBUtility.RunQuery("select * from otp_authentication where mobile_no = ? and otp = ? and otp_status = ?", 
                                       new String[]{mobile, otp, "P"}, isProdDB);
            // Cache the result
            if (result != null) {
                putInCache(cacheKey, new ArrayList<>(result));
            }
        } catch (NumberFormatException ee) {
            // Log error appropriately
            System.err.println("NumberFormatException in verifyOtp: " + ee.getMessage());
        }
        return result;
    }

    public static boolean insertMobileOtp(String mobile, String otp, boolean isProdDB) {
        boolean status = false;
        try {
            status = DBUtility.RunNonQuery("INSERT INTO otp_authentication (mobile_no, otp, otp_status) VALUES (?,?,?)", 
                                          new String[]{mobile, otp, "P"}, isProdDB);
            // Clear cache for this mobile number
            clearCacheByPrefix("verifyOtp:" + mobile);
        } catch (NumberFormatException ee) {
            System.err.println("NumberFormatException in insertMobileOtp: " + ee.getMessage());
        }
        return status;
    }

    public static boolean updateMobileOTPStatus(String mobile, String otp, boolean isProdDB) {
        boolean status = false;
        try {
            status = DBUtility.RunNonQuery("UPDATE otp_authentication SET OTP_STATUS='S' WHERE mobile_no = ? and otp = ? and otp_status = ?", 
                                          new String[]{mobile, otp, "P"}, isProdDB);
            // Clear cache for this mobile number
            clearCacheByPrefix("verifyOtp:" + mobile);
        } catch (NumberFormatException ee) {
            System.err.println("NumberFormatException in updateMobileOTPStatus: " + ee.getMessage());
        }
        return status;
    }

    public static ArrayList<HashMap<String, String>> accountUserData(String mobile, boolean isProdDB) {
        String cacheKey = generateCacheKey("accountUserData", mobile, isProdDB);
        
        // Try to get from cache
        ArrayList<HashMap<String, String>> cachedResult = getFromCache(cacheKey);
        if (cachedResult != null) {
            return new ArrayList<>(cachedResult);
        }
        
        // Get status and value from DB
        ArrayList<HashMap<String, String>> result = null;
        try {
            result = DBUtility.RunQuery("select * from account_user where mobile_no = ?", new String[]{mobile}, isProdDB);
            if (result != null) {
                putInCache(cacheKey, new ArrayList<>(result));
            }
        } catch (NumberFormatException ee) {
            System.err.println("NumberFormatException in accountUserData: " + ee.getMessage());
        }
        return result;
    }

    public static ArrayList<HashMap<String, String>> accountUserEmailData(String mobile, boolean isProdDB) {
        String cacheKey = generateCacheKey("accountUserEmailData", mobile, isProdDB);
        
        // Try to get from cache
        ArrayList<HashMap<String, String>> cachedResult = getFromCache(cacheKey);
        if (cachedResult != null) {
            return new ArrayList<>(cachedResult);
        }
        
        // Get status and value from DB
        ArrayList<HashMap<String, String>> result = null;
        try {
            result = DBUtility.RunQuery("select * from account_user where email = ?", new String[]{mobile}, isProdDB);
            if (result != null) {
                putInCache(cacheKey, new ArrayList<>(result));
            }
        } catch (NumberFormatException ee) {
            System.err.println("NumberFormatException in accountUserEmailData: " + ee.getMessage());
        }
        return result;
    }

    public static boolean updateUserPassword(String mobile, boolean isProdDB) {
        boolean status = false;
        try {
            status = DBUtility.RunNonQuery("UPDATE account_user SET password = ? WHERE mobile_no = ?", 
                                          new String[]{Constants.HEGMA_ENC_PASSWORD, mobile}, isProdDB);
            // Clear cache for this user
            clearCacheByPrefix("accountUserData:" + mobile);
            clearCacheByPrefix("getMobile");
        } catch (NumberFormatException ee) {
            System.err.println("NumberFormatException in updateUserPassword: " + ee.getMessage());
        }
        return status;
    }

    public static boolean updateUserEmailPassword(String mobile, String password, boolean isProdDB) {
        boolean status = false;
        try {
            status = DBUtility.RunNonQuery("UPDATE account_user SET password = ? WHERE email = ?", 
                                          new String[]{password, mobile}, isProdDB);
            // Clear cache for this user
            clearCacheByPrefix("accountUserEmailData:" + mobile);
            clearCacheByPrefix("getMobile");
        } catch (NumberFormatException ee) {
            System.err.println("NumberFormatException in updateUserEmailPassword: " + ee.getMessage());
        }
        return status;
    }

    public static boolean updateUserMobile(String mobile, String email, boolean isProdDB) {
        boolean status = false;
        try {
            status = DBUtility.RunNonQuery("UPDATE account_user SET mobile_no = ? WHERE email = ?", 
                                          new String[]{mobile, email}, isProdDB);
            // Clear cache for this user
            clearCacheByPrefix("accountUserEmailData:" + email);
            clearCacheByPrefix("accountUserData:" + mobile);
            clearCacheByPrefix("getMobile");
        } catch (NumberFormatException ee) {
            System.err.println("NumberFormatException in updateUserMobile: " + ee.getMessage());
        }
        return status;
    }

    public static boolean updateUserDetails(String newemail, String mobile, String firstName, String lastName, String email, boolean isProdDB) {
        boolean status = false;
        try {
            status = DBUtility.RunNonQuery("UPDATE account_user SET email = ?, mobile_no = ?, first_name = ?, last_name = ? WHERE email = ?", 
                                          new String[]{newemail, mobile, firstName, lastName, email}, isProdDB);
            // Clear cache for this user
            clearCacheByPrefix("accountUserEmailData:" + email);
            clearCacheByPrefix("accountUserEmailData:" + newemail);
            clearCacheByPrefix("accountUserData:" + mobile);
            clearCacheByPrefix("getMobile");
        } catch (NumberFormatException ee) {
            System.err.println("NumberFormatException in updateUserDetails: " + ee.getMessage());
        }
        return status;
    }
    
    public static String updateOrderStatus(String orderNo, boolean isProdDB) {
        String status = "fail";
        Connection con = null;
        PreparedStatement ps = null;

        try {
            DBConnection dbCon = new DBConnection();
            con = dbCon.getConnection(isProdDB);

            ps = con.prepareStatement(
                    "UPDATE order_order SET status='processing' WHERE number = ?");
            ps.setInt(1, Integer.parseInt(orderNo));
            ps.executeUpdate();
            status = "Success";
            // Clear cache for this order
            clearCacheByPrefix("orderPaymentData");
            clearCacheByPrefix("checkReference");
        } catch (SQLException ex) {
            status = "fail";
            System.err.println("Exception in ServiceDao class method (updateOrderStatus): " + ex.getMessage());
        } finally {
            closeResources(ps, null, con);
        }
        return status;
    }
    
    public static String updateOrderLineStatus(String orderNo, String orderLineId, boolean isProdDB) {
        String status = "fail";
        Connection con = null;
        PreparedStatement ps = null;

        try {
            DBConnection dbCon = new DBConnection();
            con = dbCon.getConnection(isProdDB);

            ps = con.prepareStatement(
                    "UPDATE order_fulfillment set status='delivered' WHERE fulfillment_order =? and order_id="
                            + "(select id from order_order where number=?)");
            ps.setInt(1, Integer.parseInt(orderLineId));
            ps.setInt(2, Integer.parseInt(orderNo));
            ps.executeUpdate();
            status = "Success";
            // Clear cache for this order
            clearCacheByPrefix("orderPaymentData");
        } catch (SQLException ex) {
            status = "fail";
            System.err.println("Exception in ServiceDao class method (updateOrderLineStatus): " + ex.getMessage());
        } finally {
            closeResources(ps, null, con);
        }
        return status;
    }

    public static String getMobile(String email, boolean isProdDB) {
        String cacheKey = generateCacheKey("getMobile", email, isProdDB);
        
        // Try to get from cache
        String cachedResult = getFromCache(cacheKey);
        if (cachedResult != null) {
            return cachedResult;
        }
        
        // Get status and value from DB
        ArrayList<HashMap<String, String>> result;
        String mobile = "";
        try {
            result = DBUtility.RunQuery("select * from account_user where email = ?", new String[]{email}, isProdDB);
            if (result != null && result.size() > 0) {
                for (int ii = 0; ii < result.size(); ii++) {
                    mobile = result.get(ii).get("mobile_no");
                }
                putInCache(cacheKey, mobile);
            }
        } catch (NumberFormatException ee) {
            System.err.println("NumberFormatException in getMobile: " + ee.getMessage());
        }
        return mobile;
    }

    public static boolean insertSearchTerm(String searchTerm, String email, String totalCount, boolean isProdDB) {
        boolean status = false;
        try {
            status = DBUtility.RunNonQuery("insert into search_searchedterms(search_datetime, email_id, search_term, result_count)\n"
                    + "values (now(), ?, ?, ?)", new String[]{email, searchTerm, totalCount}, isProdDB);
            // Don't cache insert operations
        } catch (NumberFormatException ee) {
            System.err.println("NumberFormatException in insertSearchTerm: " + ee.getMessage());
        }
        return status;
    }

    public static ArrayList<HashMap<String, String>> checkReference(String referenceNo, boolean isProdDB) {
        String cacheKey = generateCacheKey("checkReference", referenceNo, isProdDB);
        
        // Try to get from cache
        ArrayList<HashMap<String, String>> cachedResult = getFromCache(cacheKey);
        if (cachedResult != null) {
            return new ArrayList<>(cachedResult);
        }
        
        // Get status and value from DB
        ArrayList<HashMap<String, String>> result = null;
        try {
            result = DBUtility.RunQuery("select * from order_order where reference_no = ?", new String[]{referenceNo}, isProdDB);
            if (result != null) {
                putInCache(cacheKey, new ArrayList<>(result));
            }
        } catch (NumberFormatException ee) {
            System.err.println("NumberFormatException in checkReference: " + ee.getMessage());
        }
        return result;
    }

    public static boolean updateOrderReference(String referenceNo, String bankReferenceNo, String checkoutToken, boolean isProdDB) {
        boolean status = false;
        try {
            status = DBUtility.RunNonQuery("UPDATE order_order SET reference_no = ?, bank_reference_no = ? WHERE checkout_token = ?", 
                                          new String[]{referenceNo, bankReferenceNo, checkoutToken}, isProdDB);
            // Clear cache for this order
            clearCacheByPrefix("orderPaymentData:" + checkoutToken);
            clearCacheByPrefix("checkReference");
        } catch (NumberFormatException ee) {
            System.err.println("NumberFormatException in updateOrderReference: " + ee.getMessage());
        }
        return status;
    }

    public static boolean insertOrderPayment(String orderId, String checkoutToken, String amount, boolean isProdDB) {
        boolean status = false;
        try {
            status = DBUtility.RunNonQuery("INSERT INTO order_payment (order_id, checkout_token, amount, status, session_id) VALUES (?,?,?,?,?)",
                    new String[]{checkoutToken, checkoutToken, amount, "Pending", orderId}, isProdDB);
            // Don't cache insert operations
        } catch (NumberFormatException ee) {
            System.err.println("NumberFormatException in insertOrderPayment: " + ee.getMessage());
        }
        return status;
    }

    public static ArrayList<HashMap<String, String>> orderPaymentData(String orderId, boolean isProdDB) {
        String cacheKey = generateCacheKey("orderPaymentData", orderId, isProdDB);
        
        // Try to get from cache
        ArrayList<HashMap<String, String>> cachedResult = getFromCache(cacheKey);
        if (cachedResult != null) {
            return new ArrayList<>(cachedResult);
        }
        
        // Get status and value from DB
        ArrayList<HashMap<String, String>> result = null;
        try {
            result = DBUtility.RunQuery("select * from order_payment where order_id = ?", new String[]{orderId}, isProdDB);
            if (result != null) {
                putInCache(cacheKey, new ArrayList<>(result));
            }
        } catch (NumberFormatException ee) {
            System.err.println("NumberFormatException in orderPaymentData: " + ee.getMessage());
        }
        return result;
    }

    public static boolean updateOrderPaymentStatus(String orderId, String status1, boolean isProdDB) {
        boolean status = false;
        try {
            status = DBUtility.RunNonQuery("UPDATE order_payment SET status = ? WHERE order_id = ?", 
                                          new String[]{status1, orderId}, isProdDB);
            // Clear cache for this payment
            clearCacheByPrefix("orderPaymentData:" + orderId);
        } catch (NumberFormatException ee) {
            System.err.println("NumberFormatException in updateOrderPaymentStatus: " + ee.getMessage());
        }
        return status;
    }

    public static String updateOrderRating(int orderNo, int rating, String review, String productName, boolean isProdDB) {
        String status = "fail";
        Connection con = null;
        PreparedStatement ps = null;

        try {
            DBConnection dbCon = new DBConnection();
            con = dbCon.getConnection(isProdDB);

            ps = con.prepareStatement(
                    "update order_orderline set rating = ?, review = ? where order_id = (select id from order_order "
                    + "where number = ?) and product_name = ?");
            ps.setInt(1, rating);
            ps.setString(2, review);
            ps.setInt(3, orderNo);
            ps.setString(4, productName);
            ps.executeUpdate();
            status = "Success";
            // Clear cache for this order's rating
            clearCacheByPrefix("avgProductRating:" + orderNo);
        } catch (SQLException ex) {
            status = "fail";
            System.err.println("Exception in ServiceDao class method (updateOrderRating): " + ex.getMessage());
        } finally {
            closeResources(ps, null, con);
        }
        return status;
    }

    public static double avgProductRating(int orderNo, boolean isProdDB) {
        String cacheKey = generateCacheKey("avgProductRating", orderNo, isProdDB);
        
        // Try to get from cache
        Double cachedResult = getFromCache(cacheKey);
        if (cachedResult != null) {
            return cachedResult;
        }
        
        double avgRating = 0;
        String sql;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            DBConnection dbCon = new DBConnection();
            con = dbCon.getConnection(isProdDB);
            sql = "select avg(rating) from order_orderline where variant_id in \n"
                    + "(select variant_id from product_productvariant where product_id=\n"
                    + " (select product_id from product_productvariant where id =\n"
                    + "  (select variant_id from order_orderline where order_id = (\n"
                    + "  select id from order_order where number = ?))))";
            pstmt = con.prepareStatement(sql);
            pstmt.setInt(1, orderNo);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                avgRating = Double.parseDouble(rs.getString("avg"));
            }
            putInCache(cacheKey, avgRating);
        } catch (NumberFormatException | SQLException ex) {
            System.err.println("Exception in ServiceDao class method (avgProductRating): " + ex.getMessage());
        } finally {
            closeResources(pstmt, rs, con);
        }
        return avgRating;
    }

    public static String updateProductRating(int orderNo, double rating, boolean isProdDB) {
        String status = "fail";
        Connection con = null;
        PreparedStatement ps = null;

        try {
            DBConnection dbCon = new DBConnection();
            con = dbCon.getConnection(isProdDB);

            ps = con.prepareStatement(
                    "update product_product set rating = ? where id =\n"
                    + "   (select product_id from product_productvariant where id =\n"
                    + "  (select variant_id from order_orderline where order_id = (\n"
                    + "  select id from order_order where number = ?)))");
            ps.setDouble(1, rating);
            ps.setInt(2, orderNo);
            ps.executeUpdate();
            status = "Success";
            // Clear cache for this product's rating
            clearCacheByPrefix("avgProductRating:" + orderNo);
        } catch (SQLException ex) {
            status = "fail";
            System.err.println("Exception in ServiceDao class method (updateProductRating): " + ex.getMessage());
        } finally {
            closeResources(ps, null, con);
        }
        return status;
    }
    
    // Utility method to close database resources properly
    private static void closeResources(PreparedStatement ps, ResultSet rs, Connection con) {
        try {
            if (rs != null) {
                rs.close();
            }
            if (ps != null) {
                ps.close();
            }
            if (con != null) {
                con.close();
            }
        } catch (SQLException e) {
            System.err.println("Exception while closing resources: " + e.getMessage());
        }
    }
}
