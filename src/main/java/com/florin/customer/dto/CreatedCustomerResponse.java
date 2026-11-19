package com.florin.customer.dto;

public record CreatedCustomerResponse(
        boolean success, String message, CustomerResponse createdCustomer) {}
