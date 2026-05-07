<%-- 
    Document   : home
    Created on : May 30, 2023, 10:09:33 AM
    Author     : admin
--%>

<%@page import="java.util.Enumeration"%>
<%@page import="com.dmart.model.Constants"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<%
    String encRequest = (String) request.getParameter("encRequest");
%>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <script src="https://sdk.cashfree.com/js/v3/cashfree.js"></script>
        <title>JSP Page</title>
    </head>
    <body onload = "callPg()">
        <script>

            function callPg() {
                
                const cashfree = Cashfree({
                    mode: "production"
                });
                let checkoutOptions = {
                    paymentSessionId: '<%=encRequest%>',
                    redirectTarget: "_self"
                };
                cashfree.checkout(checkoutOptions);
            }
        </script>
    </body>
</html>
