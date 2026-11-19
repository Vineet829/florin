package com.florin.customer.dto;

import java.math.BigDecimal;

public record CustomerBalance(String currency, BigDecimal outstanding) {}
