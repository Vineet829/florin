package com.florin.document;

public record DocumentPdf(String fileName, byte[] content, DocumentEnvelope envelope) {}
