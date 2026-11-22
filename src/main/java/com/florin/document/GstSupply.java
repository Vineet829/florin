package com.florin.document;

import com.florin.business.Business;
import com.florin.common.ApiException;
import com.florin.settings.BusinessSettings;
import com.florin.settings.SettingsService;
import com.florin.tax.GstStates;
import com.florin.tax.Gstin;
import com.florin.tax.SupplyType;
import com.florin.tax.TaxRegime;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class GstSupply {

    public static final String LUT = "LUT";
    public static final String IGST = "IGST";
    private static final String GST_NUMBER = "[A-Za-z0-9/-]{1,16}";

    private final SettingsService settingsService;

    public GstSupply(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public void place(Document document, String requestedPlace, Boolean reverseCharge, String exportType) {
        BusinessSettings settings = settingsService.forBusiness(document.getBusiness().getPkid());
        document.setTaxRegime(settings.getTaxRegime());
        if (settings.getTaxRegime() != TaxRegime.GST_INDIA) {
            document.setSellerStateCode(null);
            document.setPlaceOfSupply(null);
            document.setSupplyType(null);
            document.setReverseCharge(false);
            document.setLutReference(null);
            return;
        }
        String seller = sellerState(document.getBusiness());
        String place = requestedPlace == null || requestedPlace.isBlank()
                ? defaultPlace(document.getCustomerSnapshot(), seller)
                : requestedPlace.trim();
        if (place != null && !GstStates.isPlace(place)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose the place of supply from the list");
        }
        SupplyType type = place == null ? null : supplyType(document, settings, place, seller, exportType);
        boolean recipientPays = reverseCharge != null ? reverseCharge : document.isReverseCharge();
        if (recipientPays && type != null && type.export()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reverse charge doesn't apply to exports");
        }
        document.setSellerStateCode(seller);
        document.setPlaceOfSupply(place);
        document.setSupplyType(type);
        document.setReverseCharge(recipientPays);
        document.setLutReference(type == SupplyType.EXPORT_LUT ? settings.getLutReference() : null);
    }

    public void placeAtIssue(Document document, CustomerSnapshot saved) {
        place(document, chosenPlace(document, saved), null, null);
        zeroRatedExport(document);
    }

    static String chosenPlace(Document document, CustomerSnapshot saved) {
        String place = document.getPlaceOfSupply();
        return place == null || place.equals(defaultPlace(saved, document.getSellerStateCode())) ? null : place;
    }

    static void zeroRatedExport(Document document) {
        if (document.getSupplyType() == SupplyType.EXPORT_LUT) {
            document.getBillingItems().forEach(item -> item.setTaxRate(BigDecimal.ZERO));
        }
    }

    static void requireIssuable(Document document) {
        if (document.getTaxRegime() != TaxRegime.GST_INDIA) {
            return;
        }
        if (document.getSellerStateCode() == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Add your business's GSTIN under Settings > Business before issuing in GST mode");
        }
        if (document.getPlaceOfSupply() == null || document.getSupplyType() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Choose the place of supply before issuing");
        }
        if (document.getCurrency() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Choose the currency before issuing");
        }
        if (!"INR".equals(document.getCurrency())
                && (document.getExchangeRate() == null || !"INR".equals(document.getExchangeBase()))) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "A document in " + document.getCurrency() + " needs an exchange rate to INR in GST mode");
        }
    }

    static void requireNumber(Document document) {
        if (document.getTaxRegime() == TaxRegime.GST_INDIA && !document.getDocumentNumber().matches(GST_NUMBER)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "In GST mode a document number can be at most 16 letters, digits, / and -");
        }
    }

    private static SupplyType supplyType(
            Document document, BusinessSettings settings, String place, String seller, String exportType) {
        if (!GstStates.OUTSIDE_INDIA.equals(place)) {
            return place.equals(seller) ? SupplyType.INTRA : SupplyType.INTER;
        }
        SupplyType previous = document.getSupplyType();
        SupplyType type = LUT.equals(exportType)
                ? SupplyType.EXPORT_LUT
                : IGST.equals(exportType)
                        ? SupplyType.EXPORT_IGST
                        : previous != null && previous.export()
                                ? previous
                                : settings.getLutReference() != null ? SupplyType.EXPORT_LUT : SupplyType.EXPORT_IGST;
        if (exportType != null && !LUT.equals(exportType) && !IGST.equals(exportType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Export under a LUT or with IGST");
        }
        if (type == SupplyType.EXPORT_LUT && settings.getLutReference() == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Add your LUT reference under Settings > Documents to export without IGST");
        }
        return type;
    }

    private static String defaultPlace(CustomerSnapshot client, String seller) {
        if (client != null && client.getGstin() != null) {
            return Gstin.stateCode(client.getGstin());
        }
        if (client != null && client.getStateCode() != null) {
            return client.getStateCode();
        }
        return seller;
    }

    private static String sellerState(Business business) {
        String taxId = business.getTaxId() == null ? null : business.getTaxId().trim().toUpperCase(Locale.ROOT);
        return Gstin.isValid(taxId) ? Gstin.stateCode(taxId) : null;
    }
}
