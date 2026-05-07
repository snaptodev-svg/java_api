/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.dmart.db;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author behal
 */
public class DBUtility {
    public static ArrayList<HashMap<String,String>> RunQuery(String query, String[] vals, boolean isProdDB)
    {
        ArrayList result = new ArrayList();
        ResultSet rs = null;
        Connection con = null;
        PreparedStatement pstmt = null;
        try
        {
            
            DBConnection dbCon = new DBConnection();
            con = dbCon.getConnection(isProdDB);
            
            
            pstmt = con.prepareStatement(query);

            if (vals != null)
            {
                for (int ii = 0; ii < vals.length; ii++)
                {
                    pstmt.setString(ii + 1,vals[ii]);
                }
            }
            rs = pstmt.executeQuery();
            if (rs != null){
                ResultSetMetaData meta = rs.getMetaData();
                while(rs.next()){
                    HashMap<String, String> hm = new HashMap<String, String>();
                    for (int ii = 0; ii < meta.getColumnCount(); ii++){
                        hm.put(meta.getColumnLabel(ii+1),rs.getString(ii+1));
                    }
                    result.add(hm);
                }
            }
        }
        catch (Exception ex)
    {
      System.out.println("Exception in RunQuery class method (RunQuery)");
      
      try
      {
          if (rs != null){
              rs.close();
          }
        if (pstmt != null) {
          pstmt.close();
        }
        if (con != null) {
          con.close();
        }
      }
      catch (Exception e)
      {
            System.out.println("Exception in RunQuery class method (RunQuery) while closing connection");
            e.printStackTrace();
          }
        }
        finally
        {
            
          try
          {
              if (rs != null){
              rs.close();
          }
            if (pstmt != null) {
              pstmt.close();
            }
            if (con != null) {
              con.close();
            }
          }
          catch (Exception e)
          {
            System.out.println("Exception in LoginDao class method (getLoginStatus) while closing connection");
            e.printStackTrace();
          }
        }
        return result;
        
    }

    public static boolean RunNonQuery(String query, String[] vals, boolean isProdDB)
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean result = false;
        try
        {
            
            DBConnection dbCon = new DBConnection();
            con = dbCon.getConnection(isProdDB);
            
            
            pstmt = con.prepareStatement(query);

            if (vals != null)
            {
                for (int ii = 0; ii < vals.length; ii++)
                {
                    pstmt.setString(ii + 1,vals[ii]);
                }
            }
            pstmt.execute();
            result=true;
        }
        catch (Exception ex)
    {
      System.out.println("Exception in RunNonQuery class method (RunNonQuery)");
      result=false;
      try
      {
        
        if (pstmt != null) {
          pstmt.close();
        }
        if (con != null) {
          con.close();
        }
      }
      catch (Exception e)
      {
            System.out.println("Exception in RunNonQuery class method (RunNonQuery) while closing connection");
            e.printStackTrace();
          }
        }
        finally
        {
          try
          {
            
            if (pstmt != null) {
              pstmt.close();
            }
            if (con != null) {
              con.close();
            }
          }
          catch (Exception e)
          {
            System.out.println("Exception in RunNonQuery class method (RunNonQuery) while closing connection");
            e.printStackTrace();
          }
        }
        return result;        
    }
    
    
    public static void RunProcedure(String procName, String[] vals, boolean isProdDB)
    {
        Connection con = null;
        CallableStatement cstmt = null;
        try
        {
            
            DBConnection dbCon = new DBConnection();
            con = dbCon.getConnection(isProdDB);
            
            
            cstmt = con.prepareCall(procName);

            if (vals != null)
            {
                for (int ii = 0; ii < vals.length; ii++)
                {
                    cstmt.setString(ii + 1,vals[ii]);
                }
            }
            cstmt.execute();
        }
        catch (Exception ex)
    {
      System.out.println("Exception in RunProcedure class method (RunProcedure)");
      
      try
      {
        
        if (cstmt != null) {
          cstmt.close();
        }
        if (con != null) {
          con.close();
        }
      }
      catch (Exception e)
      {
            System.out.println("Exception in RunProcedure class method (RunProcedure) while closing connection");
            e.printStackTrace();
          }
        }
        finally
        {
          try
          {
            
            if (cstmt != null) {
              cstmt.close();
            }
            if (con != null) {
              con.close();
            }
          }
          catch (Exception e)
          {
            System.out.println("Exception in RunProcedure class method (RunProcedure) while closing connection");
            e.printStackTrace();
          }
        }
        
        
    }
    
    public static String RunReturnableProcedure(String procName, String[] vals, boolean isProdDB)
    {
        Connection con = null;
        CallableStatement cstmt = null;
        String res = null;
        try
        {
            
            DBConnection dbCon = new DBConnection();
            con = dbCon.getConnection(isProdDB);
            
            
            cstmt = con.prepareCall(procName);

            if (vals != null)
            {
                for (int ii = 0; ii < vals.length; ii++)
                {
                    cstmt.setString(ii + 1,vals[ii]);
                }
            }
            
            cstmt.registerOutParameter(vals.length + 1, 12);
            
            cstmt.execute();
            res = cstmt.getString(vals.length + 1);
            String aa = res;
        }
        catch (Exception ex)
    {
      System.out.println("Exception in RunReturnableProcedure class method (RunReturnableProcedure)");
      
      try
      {
       
        if (cstmt != null) {
          cstmt.close();
        }
        if (con != null) {
          con.close();
        }
      }
      catch (Exception e)
      {
            System.out.println("Exception in RunReturnableProcedure class method (RunReturnableProcedure) while closing connection");
            e.printStackTrace();
          }
        }
        finally
        {
          try
          {
            
            if (cstmt != null) {
              cstmt.close();
            }
            if (con != null) {
              con.close();
            }
          }
          catch (Exception e)
          {
            System.out.println("Exception in RunReturnableProcedure class method (RunReturnableProcedure) while closing connection");
            e.printStackTrace();
          }
        }
        
        return res;
    }
    
}
