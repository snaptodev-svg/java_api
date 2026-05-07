package com.dmart.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DBConnection {

    public Connection getConnection(boolean isProdDB) {
        Connection con = null;
        String dbUrl;
        String userName;
        String password;
        if(isProdDB) {
            dbUrl = "jdbc:postgresql://snapto-db.cbu4e2ymkgr8.ap-south-1.rds.amazonaws.com:5432/postgres?sslmode=require";
            userName = System.getenv("DB_USERNAME");
            password = System.getenv("DB_PASSWORD");
        } else {
            dbUrl = "jdbc:postgresql://db:5432/saleor";
            userName = "saleor";
            password = "saleor";
        }
        try {
            Class.forName("org.postgresql.Driver");
            con = DriverManager.getConnection(dbUrl, userName, password);
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println(e.getClass().getName() + ": " + e.getMessage());
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(DBConnection.class.getName()).log(Level.SEVERE, null, ex);
        }
        return con;
    }
}


/* Location:           F:\QMM\Projects\RocketInPocket\java code\agent\WEB-INF\classes\
 * Qualified Name:     com.db.DBConnection
 * JD-Core Version:    0.7.0.1
 */
