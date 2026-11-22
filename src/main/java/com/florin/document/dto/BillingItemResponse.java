package com.florin.document.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.florin.document.BillingItem;
import com.florin.document.DocumentTotals;
import java.math.BigDecimal;

public record BillingItemResponse(
        String itemName,
        BigDecimal unitPrice,
        BigDecimal quantity,
        String discount,
        BigDecimal lineTotal,
        @JsonInclude(JsonInclude.Include.NON_NULL) String unit,
        BigDecimal taxRate,
        @JsonInclude(JsonInclude.Include.NON_NULL) String hsnSac) {

    public static BillingItemResponse from(BillingItem item) {
        return new BillingItemResponse(
                item.getItemName(),
                item.getUnitPrice(),
                plain(item.getQuantity()),
                plain(item.getDiscount()).toPlainString(),
                DocumentTotals.lineTotal(item),
                item.getUnit(),
                plain(item.getTaxRate()),
                item.getHsnSac());
    }

    public static BigDecimal plain(BigDecimal value) {
        BigDecimal stripped = (value == null ? BigDecimal.ZERO : value).stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
