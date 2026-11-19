package com.florin.document.dto;

public record UpdatedDocumentResponse(
        boolean success, String message, DocumentResponse updatedDocument) {}
