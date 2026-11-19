package com.florin.customer.dto;

public record UpdatedCustomerResponse(
        boolean success, String message, CustomerResponse updatedCustomerInfo) {}
