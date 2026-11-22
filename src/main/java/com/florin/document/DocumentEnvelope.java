package com.florin.document;

import com.florin.settings.EmailTemplateKind;
import java.util.Map;
import java.util.UUID;

public record DocumentEnvelope(
        EmailTemplateKind kind,
        String title,
        String recipientName,
        String recipientEmail,
        Map<String, String> values,
        UUID documentId) {}
