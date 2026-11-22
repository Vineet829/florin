package com.florin.document;

import com.florin.common.ApiException;
import com.florin.user.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditNoteService {

    private final DocumentRepository documentRepository;
    private final DocumentActivity activity;

    public CreditNoteService(DocumentRepository documentRepository, DocumentActivity activity) {
        this.documentRepository = documentRepository;
        this.activity = activity;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Document create(Document credited, User author) {
        if (credited.getDocumentType() != DocumentType.INVOICE && credited.getDocumentType() != DocumentType.RECEIPT) {
            throw new ApiException(HttpStatus.CONFLICT, "Only an invoice or a receipt can be credited");
        }
        if (credited.isDraft()) {
            throw new ApiException(HttpStatus.CONFLICT, "Issue this draft before crediting it");
        }
        if (credited.isVoid()) {
            throw new ApiException(HttpStatus.CONFLICT, "A void document can't be credited");
        }
        if (DocumentTotals.creditable(credited).signum() <= 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT, credited.getDocumentNumber() + " is already credited in full");
        }
        Document note = new Document();
        note.setBusiness(credited.getBusiness());
        note.setCreatedBy(author);
        note.setCredited(credited);
        note.setCustomer(credited.getCustomer());
        note.setCustomerSnapshot(DocumentService.copyOf(credited.getCustomerSnapshot()));
        note.setDocumentType(DocumentType.CREDIT_NOTE);
        note.setCurrency(credited.getCurrency());
        note.setExchangeRate(credited.getExchangeRate());
        note.setExchangeBase(credited.getExchangeBase());
        note.setTaxRegime(credited.getTaxRegime());
        note.setSellerStateCode(credited.getSellerStateCode());
        note.setPlaceOfSupply(credited.getPlaceOfSupply());
        note.setSupplyType(credited.getSupplyType());
        note.setReverseCharge(credited.isReverseCharge());
        note.setLutReference(credited.getLutReference());
        note.setBillingItems(credited.getBillingItems().stream().map(DocumentService::copyOf).toList());
        DocumentTotals.apply(note);
        credited.getCreditNotes().add(note);
        documentRepository.save(note);
        activity.record(note, DocumentEventType.CREATED, null, author);
        return note;
    }

    static void requireRoom(Document note, LocalDate issueDate) {
        Document credited = note.getCredited();
        if (credited.state() != DocumentState.ISSUED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Only an issued " + credited.getDocumentType().getValue().toLowerCase() + " can be credited");
        }
        if (credited.getIssueDate() != null && issueDate.isBefore(credited.getIssueDate())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "A credit note can't be dated before " + credited.getDocumentNumber() + " was issued");
        }
        if (note.getTotal().signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A credit note needs an amount to credit");
        }
        BigDecimal left = DocumentTotals.creditable(credited);
        if (note.getTotal().compareTo(left) > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "That is more than the " + left.toPlainString() + " left to credit on "
                            + credited.getDocumentNumber());
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void issued(Document note, User actor) {
        Document credited = note.getCredited();
        DocumentTotals.settle(credited);
        activity.record(
                credited,
                DocumentEventType.CREDITED,
                note.getDocumentNumber() + " · " + DocumentActivity.amount(note, note.getTotal()),
                actor);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void voided(Document note, User actor) {
        Document credited = note.getCredited();
        RefundService.requireCreditRefunded(credited, DocumentTotals.credited(credited));
        DocumentTotals.settle(credited);
        activity.record(credited, DocumentEventType.CREDIT_VOIDED, note.getDocumentNumber(), actor);
    }

    static void requireNoCreditNotes(Document document) {
        if (document.getCreditNotes().stream().anyMatch(note -> !note.isVoid())) {
            throw new ApiException(HttpStatus.CONFLICT, "Void or delete its credit notes before voiding it");
        }
    }
}
