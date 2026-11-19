package com.florin.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.florin.document.Document;
import com.florin.document.DocumentState;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// A credit note made against an invoice or receipt, in any state.
public record CreditNoteLink(
        @JsonProperty("_id") UUID id, String documentNumber, DocumentState state, LocalDate issueDate, BigDecimal total) {

    public static CreditNoteLink from(Document note) {
        return new CreditNoteLink(
                note.getId(), note.getDocumentNumber(), note.state(), note.getIssueDate(), note.getTotal());
    }
}
