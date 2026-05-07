/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.dmart.model;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * @version 1.0
 * @since Jul 04, 2022
 * @author Rahul(QMM Technologies Pvt. Ltd.)
 */
@XmlRootElement(name = "Error")
public class Error {

    private String errorCode;
    private String message;

    public Error() {
    }

    public Error(String ErrorCode, String Message) {
        this.errorCode = ErrorCode;
        this.message = Message;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getErrorCodeInt() {
        return Integer.valueOf(errorCode);
    }

    public void setErrorCode(String ErrorCode) {
        this.errorCode = ErrorCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String Message) {
        this.message = Message;
    }
}
