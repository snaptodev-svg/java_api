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

/**
 *
 * @author Rahul
 */
public class ServiceDao {

    public static ArrayList<HashMap<String, String>> verifyOtp(String mobile, String otp, boolean isProdDB) {

        //Get status and value from DB
        ArrayList<HashMap<String, String>> result = null;
        try {
            result = DBUtility.RunQuery("select * from otp_authentication where mobile_no = ? and otp = ? and otp_status = ?", new String[]{mobile, otp, "P"}, isProdDB);

        } catch (NumberFormatException ee) {

        }
        return result;
    }

    public static boolean insertMobileOtp(String mobile, String otp, boolean isProdDB) {

        boolean status = false;
        try {
            status = DBUtility.RunNonQuery("INSERT INTO otp_authentication (mobile_no, otp, otp_status) VALUES (?,?,?)", new String[]{mobile, otp, "P"}, isProdDB);

        } catch (NumberFormatException ee) {

        }
        return status;
    }

    public static boolean updateMobileOTPStatus(String mobile, String otp, boolean isProdDB) {

        //Get status and value from DB
        boolean status = false;
        try {
            status = DBUtility.RunNonQuery("UPDATE otp_authentication SET OTP_STATUS='S' WHERE mobile_no = ? and otp = ? and otp_status = ?", new String[]{mobile, otp, "P"}, isProdDB);
        } catch (NumberFormatException ee) {

        }
        return status;
    }

    public static ArrayList<HashMap<String, String>> accountUserData(String mobile, boolean isProdDB) {

        //Get status and value from DB
        ArrayList<HashMap<String, String>> result = null;
        try {
            result = DBUtility.RunQuery("select * from account_user where mobile_no = ?", new String[]{mobile}, isProdDB);

        } catch (NumberFormatException ee) {

        }
        return result;
    }

    public static ArrayList<HashMap<String, String>> accountUserEmailData(String mobile, boolean isProdDB) {

        //Get status and value from DB
        ArrayList<HashMap<String, String>> result = null;
        try {
            result = DBUtility.RunQuery("select * from account_user where email = ?", new String[]{mobile}, isProdDB);

        } catch (NumberFormatException ee) {

        }
        return result;
    }

    public static boolean updateUserPassword(String mobile, boolean isProdDB) {

        //Get status and value from DB
        boolean status = false;
        try {
            status = DBUtility.RunNonQuery("UPDATE account_user SET password = ? WHERE mobile_no = ?", new String[]{Constants.HEGMA_ENC_PASSWORD, mobile}, isProdDB);
        } catch (NumberFormatException ee) {

        }
        return status;
    }

    public static boolean updateUserEmailPassword(String mobile, String password, boolean isProdDB) {

        //Get status and value from DB
        boolean status = false;
        try {
            status = DBUtility.RunNonQuery("UPDATE account_user SET password = ? WHERE email = ?", new String[]{password, mobile}, isProdDB);
        } catch (NumberFormatException ee) {

        }
        return status;
    }

    public static boolean updateUserMobile(String mobile, String email, boolean isProdDB) {

        //Get status and value from DB
        boolean status = false;
        try {
            status = DBUtility.RunNonQuery("UPDATE account_user SET mobile_no = ? WHERE email = ?", new String[]{mobile, email}, isProdDB);
        } catch (NumberFormatException ee) {

        }
        return status;
    }

    public static boolean updateUserDetails(String newemail, String mobile, String firstName, String lastName, String email, boolean isProdDB) {

        //Get status and value from DB
        boolean status = false;
        try {
            status = DBUtility.RunNonQuery("UPDATE account_user SET email = ?, mobile_no = ?, first_name = ?, last_name = ? WHERE email = ?", new String[]{newemail, mobile, firstName, lastName, email}, isProdDB);
        } catch (NumberFormatException ee) {

        }
        return status;
    }
    
    public static String updateOrderStatus(String orderNo, boolean isProdDB) {

        //Get status and value from DB
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
        } catch (SQLException ex) {
            status = "fail";
            System.out.println("Exception in ServiceDao class method (updateStatus)");
            try {
                if (ps != null) {
                    ps.close();
                }
                if (con != null) {
                    con.close();
                }
            } catch (SQLException e) {
                System.out.println("Exception in ServiceDao class method (updateStatus) while closing connection");
            }
        } finally {
            try {
                if (ps != null) {
                    ps.close();
                }
                if (con != null) {
                    con.close();
                }
            } catch (SQLException e) {
                System.out.println("Exception in ServiceDao class method (updateStatus) while closing connection");
            }
        }
        return status;
    }
    
    public static String updateOrderLineStatus(String orderNo, String orderLineId, boolean isProdDB) {

        //Get status and value from DB
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
        } catch (SQLException ex) {
            status = "fail";
            System.out.println("Exception in ServiceDao class method (updateStatus)");
            try {
                if (ps != null) {
                    ps.close();
                }
                if (con != null) {
                    con.close();
                }
            } catch (SQLException e) {
                System.out.println("Exception in ServiceDao class method (updateStatus) while closing connection");
            }
        } finally {
            try {
                if (ps != null) {
                    ps.close();
                }
                if (con != null) {
                    con.close();
                }
            } catch (SQLException e) {
                System.out.println("Exception in ServiceDao class method (updateStatus) while closing connection");
            }
        }
        return status;
    }

    public static String getMobile(String email, boolean isProdDB) {

        //Get status and value from DB
        ArrayList<HashMap<String, String>> result;
        String mobile = "";
        try {
            result = DBUtility.RunQuery("select * from account_user where email = ?", new String[]{email}, isProdDB);
            if (result != null && result.size() > 0) {
                for (int ii = 0; ii < result.size(); ii++) {
                    mobile = result.get(ii).get("mobile_no");
                }
            }

        } catch (NumberFormatException ee) {

        }
        return mobile;
    }

    public static boolean insertSearchTerm(String searchTerm, String email, String totalCount, boolean isProdDB) {

        boolean status = false;
        try {
            status = DBUtility.RunNonQuery("insert into search_searchedterms(search_datetime, email_id, search_term, result_count)\n"
                    + "values (now(), ?, ?, ?)", new String[]{email, searchTerm, totalCount}, isProdDB);

        } catch (NumberFormatException ee) {

        }
        return status;
    }

    public static ArrayList<HashMap<String, String>> checkReference(String referenceNo, boolean isProdDB) {

        //Get status and value from DB
        ArrayList<HashMap<String, String>> result = null;
        try {
            result = DBUtility.RunQuery("select * from order_order where reference_no = ?", new String[]{referenceNo}, isProdDB);

        } catch (NumberFormatException ee) {

        }
        return result;
    }

    public static boolean updateOrderReference(String referenceNo, String bankReferenceNo, String checkoutToken, boolean isProdDB) {

        //Get status and value from DB
        boolean status = false;
        try {
            status = DBUtility.RunNonQuery("UPDATE order_order SET reference_no = ?, bank_reference_no = ? WHERE checkout_token = ?", new String[]{referenceNo, bankReferenceNo, checkoutToken}, isProdDB);
        } catch (NumberFormatException ee) {

        }
        return status;
    }

    public static boolean insertOrderPayment(String orderId, String checkoutToken, String amount, boolean isProdDB) {

        boolean status = false;
        try {
            status = DBUtility.RunNonQuery("INSERT INTO order_payment (order_id, checkout_token, amount, status, session_id) VALUES (?,?,?,?,?)",
                    new String[]{checkoutToken, checkoutToken, amount, "Pending", orderId}, isProdDB);

        } catch (NumberFormatException ee) {

        }
        return status;
    }

    public static ArrayList<HashMap<String, String>> orderPaymentData(String orderId, boolean isProdDB) {

        //Get status and value from DB
        ArrayList<HashMap<String, String>> result = null;
        try {
            result = DBUtility.RunQuery("select * from order_payment where order_id = ?", new String[]{orderId}, isProdDB);

        } catch (NumberFormatException ee) {

        }
        return result;
    }

    public static boolean updateOrderPaymentStatus(String orderId, String status1, boolean isProdDB) {

        //Get status and value from DB
        boolean status = false;
        try {
            status = DBUtility.RunNonQuery("UPDATE order_payment SET status = ? WHERE order_id = ?", new String[]{status1, orderId}, isProdDB);
        } catch (NumberFormatException ee) {

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
        } catch (SQLException ex) {
            status = "fail";
            System.out.println("Exception in ServiceDao class method (updateStatus)");
            try {
                if (ps != null) {
                    ps.close();
                }
                if (con != null) {
                    con.close();
                }
            } catch (SQLException e) {
                System.out.println("Exception in ServiceDao class method (updateStatus) while closing connection");
            }
        } finally {
            try {
                if (ps != null) {
                    ps.close();
                }
                if (con != null) {
                    con.close();
                }
            } catch (SQLException e) {
                System.out.println("Exception in ServiceDao class method (updateStatus) while closing connection");
            }
        }
        return status;
    }

    public static double avgProductRating(int orderNo, boolean isProdDB) {

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
        } catch (NumberFormatException | SQLException ex) {
            System.out.println("Exception in ServiceDao class method (getApiKey)");
            try {
                if (rs != null) {
                    rs.close();
                }
                if (pstmt != null) {
                    pstmt.close();
                }
                if (con != null) {
                    con.close();
                }
            } catch (SQLException e) {
                System.out.println("Exception in ServiceDao class method (getApiKey) while closing connection");
            }
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
                if (pstmt != null) {
                    pstmt.close();
                }
                if (con != null) {
                    con.close();
                }
            } catch (SQLException e) {
                System.out.println("Exception in ServiceDao class method (getApiKey) while closing connection");
            }
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
        } catch (SQLException ex) {
            status = "fail";
            System.out.println("Exception in ServiceDao class method (updateStatus)");
            try {
                if (ps != null) {
                    ps.close();
                }
                if (con != null) {
                    con.close();
                }
            } catch (SQLException e) {
                System.out.println("Exception in ServiceDao class method (updateStatus) while closing connection");
            }
        } finally {
            try {
                if (ps != null) {
                    ps.close();
                }
                if (con != null) {
                    con.close();
                }
            } catch (SQLException e) {
                System.out.println("Exception in ServiceDao class method (updateStatus) while closing connection");
            }
        }
        return status;
    }
}
