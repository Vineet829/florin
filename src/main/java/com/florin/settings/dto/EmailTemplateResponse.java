package com.florin.settings.dto;

import com.florin.settings.EmailTemplate;
import com.florin.settings.EmailTemplateKind;
import java.util.List;

public record EmailTemplateResponse(
        EmailTemplateKind kind,
        String label,
        String subject,
        String body,
        boolean custom,
        String standardSubject,
        String standardBody,
        List<EmailTemplateKind.Placeholder> placeholders) {

    public static EmailTemplateResponse of(EmailTemplateKind kind, EmailTemplate template) {
        return new EmailTemplateResponse(
                kind,
                kind.label(),
                template == null ? kind.standardSubject() : template.getSubject(),
                template == null ? kind.standardBody() : template.getBody(),
                template != null,
                kind.standardSubject(),
                kind.standardBody(),
                kind.placeholders());
    }
}
