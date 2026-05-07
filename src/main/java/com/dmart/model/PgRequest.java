/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.dmart.model;

/**
 *
 * @author Rahul
 */
public class PgRequest {
    
    private double order_amount;
    private String order_currency;
    private String order_id;
    private CustomerRequest customer_details;
    private OrderRequest order_meta;

    public double getOrder_amount() {
        return order_amount;
    }

    public void setOrder_amount(double order_amount) {
        this.order_amount = order_amount;
    }

    public String getOrder_currency() {
        return order_currency;
    }

    public void setOrder_currency(String order_currency) {
        this.order_currency = order_currency;
    }

    public String getOrder_id() {
        return order_id;
    }

    public void setOrder_id(String order_id) {
        this.order_id = order_id;
    }

    public CustomerRequest getCustomer_details() {
        return customer_details;
    }

    public void setCustomer_details(CustomerRequest customer_details) {
        this.customer_details = customer_details;
    }

    public OrderRequest getOrder_meta() {
        return order_meta;
    }

    public void setOrder_meta(OrderRequest order_meta) {
        this.order_meta = order_meta;
    }
}
