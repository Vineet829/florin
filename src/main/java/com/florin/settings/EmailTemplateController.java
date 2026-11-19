package com.florin.settings;

import com.florin.business.BusinessAccess;
import com.florin.business.Permission;
import com.florin.business.Requires;
import com.florin.settings.dto.EmailTemplateEnvelope;
import com.florin.settings.dto.EmailTemplateRequest;
import com.florin.settings.dto.EmailTemplateResponse;
import com.florin.settings.dto.EmailTemplatesEnvelope;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings/email-templates")
public class EmailTemplateController {

    private final EmailTemplateService emailTemplateService;

    public EmailTemplateController(EmailTemplateService emailTemplateService) {
        this.emailTemplateService = emailTemplateService;
    }

    @GetMapping
    public EmailTemplatesEnvelope list(@Requires(Permission.VIEW_RECORDS) BusinessAccess access) {
        return new EmailTemplatesEnvelope(true, emailTemplateService.list(access));
    }

    @PutMapping("/{kind}")
    public EmailTemplateEnvelope save(
            @Requires(Permission.MANAGE_BUSINESS) BusinessAccess access,
            @PathVariable String kind,
            @Valid @RequestBody EmailTemplateRequest request) {
        EmailTemplateResponse template = emailTemplateService.save(access, kind, request);
        return new EmailTemplateEnvelope(true, "Your " + template.kind().noun() + " email was saved", template);
    }

    @DeleteMapping("/{kind}")
    public EmailTemplateEnvelope reset(
            @Requires(Permission.MANAGE_BUSINESS) BusinessAccess access, @PathVariable String kind) {
        EmailTemplateResponse template = emailTemplateService.reset(access, kind);
        return new EmailTemplateEnvelope(
                true, "Your " + template.kind().noun() + " email is the standard one again", template);
    }
}
