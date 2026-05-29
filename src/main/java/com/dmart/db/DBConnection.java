package com.dmart.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DBConnection {
    private static final Logger LOGGER = Logger.getLogger(DBConnection.class.getName());

    public Connection getConnection(boolean isProdDB) {
        Connection con = null;
        String dbUrl;
        String userName;
        String password;
        String environment = isProdDB ? "PRODUCTION" : "DEVELOPMENT";
        
        try {
            if(isProdDB) {
                dbUrl = "jdbc:postgresql://snapto-db.cbu4e2ymkgr8.ap-south-1.rds.amazonaws.com:5432/postgres?sslmode=require";
                userName = "postgres";
                password = "snapto2254"; // Replace with actual password
            } else {
                dbUrl = "jdbc:postgresql://snapto-db.cbu4e2ymkgr8.ap-south-1.rds.amazonaws.com:5432/postgres?sslmode=require";
                userName = "postgres";
                password = "snapto2254"; // Replace with actual password
            }
            
            // Log connection attempt
            LOGGER.info("Attempting to connect to " + environment + " database...");
            LOGGER.fine("Database URL: " + dbUrl);
            LOGGER.fine("Username: " + userName);
            
            // Load PostgreSQL driver
            Class.forName("org.postgresql.Driver");
            LOGGER.fine("PostgreSQL driver loaded successfully");
            
            // Establish connection
            con = DriverManager.getConnection(dbUrl, userName, password);
            
            // Validate connection
            if (con != null && !con.isClosed()) {
                LOGGER.info("✓ Successfully connected to " + environment + " database");
            } else {
                LOGGER.warning("Connection object is null or already closed");
            }
            
        } catch (ClassNotFoundException ex) {
            LOGGER.log(Level.SEVERE, 
                "ERROR: PostgreSQL Driver not found. Please ensure postgresql-driver.jar is in your classpath", ex);
                
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, 
                "ERROR: Failed to connect to " + environment + " database\n" +
                "Error Code: " + ex.getErrorCode() + "\n" +
                "SQL State: " + ex.getSQLState() + "\n" +
                "Message: " + ex.getMessage(), ex);
            
            // Log specific SQL error details
            if (ex.getSQLState() != null) {
                switch (ex.getSQLState()) {
                    case "08001":
                        LOGGER.severe("CONNECTION ISSUE: Cannot connect to host. Check:\n" +
                            "  - Database host is reachable\n" +
                            "  - Port 5432 is open\n" +
                            "  - Firewall/Security groups allow connection");
                        break;
                    case "28P01":
                        LOGGER.severe("AUTHENTICATION ISSUE: Invalid username or password");
                        break;
                    case "3D000":
                        LOGGER.severe("DATABASE ISSUE: Database does not exist");
                        break;
                    case "28000":
                        LOGGER.severe("AUTHENTICATION ISSUE: Check credentials");
                        break;
                    default:
                        LOGGER.log(Level.WARNING, "SQL State: " + ex.getSQLState());
                }
            }
        }
        
        return con;
    }
}
