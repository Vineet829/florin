package com.florin.document.dto;

import java.time.LocalDate;

public record IssueDocumentRequest(LocalDate issueDate, String dueDate, String exchangeRate) {}
