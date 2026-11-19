package com.florin.document.dto;

import com.florin.document.Document;
import com.florin.tax.GstStates;
import com.florin.tax.SupplyType;
import com.florin.tax.TaxRegime;

// A GST document's supply: the seller's state, where it goes and how it is
// taxed. Within a union territory without a legislature SGST is UTGST.
public record GstResponse(
        String sellerStateCode,
        String sellerStateName,
        String placeOfSupply,
        String placeOfSupplyName,
        SupplyType supplyType,
        boolean reverseCharge,
        String lutReference,
        boolean unionTerritory) {

    public static GstResponse from(Document document) {
        if (document.getTaxRegime() != TaxRegime.GST_INDIA) {
            return null;
        }
        String place = document.getPlaceOfSupply();
        String seller = document.getSellerStateCode();
        return new GstResponse(
                seller,
                seller == null ? null : GstStates.name(seller),
                place,
                place == null ? null : GstStates.name(place),
                document.getSupplyType(),
                document.isReverseCharge(),
                document.getLutReference(),
                document.getSupplyType() == SupplyType.INTRA && GstStates.isUnionTerritoryWithoutLegislature(place));
    }
}
