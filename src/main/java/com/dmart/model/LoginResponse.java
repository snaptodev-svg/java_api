/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.dmart.model;

import java.util.ArrayList;

/**
 * @version 1.0
 * @since Aug 01, 2022
 * @author Rahul(QMM Technologies Pvt. Ltd.)
 */
public class LoginResponse {

    private boolean api_status;
    private ArrayList<LoginError> errors;
    private String refreshToken;
    private String token;
    private User user;

    public LoginResponse() {
    }

    public LoginResponse(boolean api_status) {
        this.api_status = api_status;
    }

    public boolean getApi_status() {
        return api_status;
    }

    public void setApi_status(boolean api_status) {
        this.api_status = api_status;
    }

    public ArrayList<LoginError> getErrors() {
        return errors;
    }

    public void setErrors(ArrayList<LoginError> errors) {
        this.errors = errors;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
