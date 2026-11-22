package com.florin.document.dto;

public record EmailPreviewResponse(
        boolean success, String document, String client, String to, String subject, String message) {}
