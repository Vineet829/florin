package com.florin.document;

import com.florin.business.BusinessAccess;
import com.florin.business.Permission;
import com.florin.business.Requires;
import com.florin.document.dto.EmailPreviewResponse;
import com.florin.settings.dto.EmailTemplateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmailPreviewController {

    private final DocumentMailService documentMailService;

    public EmailPreviewController(DocumentMailService documentMailService) {
        this.documentMailService = documentMailService;
    }

    @PostMapping("/api/v1/settings/email-templates/{kind}/preview")
    public EmailPreviewResponse preview(
            @Requires(Permission.MANAGE_BUSINESS) BusinessAccess access,
            @PathVariable String kind,
            @Valid @RequestBody EmailTemplateRequest request) {
        return documentMailService.preview(access, kind, request);
    }
}
