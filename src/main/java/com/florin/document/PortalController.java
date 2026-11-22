package com.florin.document;

import com.florin.auth.dto.MessageResponse;
import com.florin.business.BusinessAccess;
import com.florin.business.Permission;
import com.florin.business.Requires;
import com.florin.document.dto.ShareLinkResponse;
import com.florin.document.dto.EmailDocumentRequest;
import com.florin.document.dto.EmailDraftResponse;
import com.florin.document.dto.ShareStatusResponse;
import com.florin.document.dto.WhatsAppShareResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/{id}/portal")
public class PortalController {

    private final PortalService portalService;
    private final DocumentMailService documentMailService;

    public PortalController(PortalService portalService, DocumentMailService documentMailService) {
        this.portalService = portalService;
        this.documentMailService = documentMailService;
    }

    @GetMapping
    public ShareStatusResponse status(
            @Requires(Permission.VIEW_RECORDS) BusinessAccess access, @PathVariable String id) {
        return portalService.status(access, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShareLinkResponse create(@Requires(Permission.EDIT_RECORDS) BusinessAccess access, @PathVariable String id) {
        return portalService.create(access, id);
    }

    @GetMapping("/email")
    public EmailDraftResponse emailDraft(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access, @PathVariable String id) {
        EmailDraft draft = documentMailService.portalDraft(access, id);
        return new EmailDraftResponse(true, draft.to(), draft.subject(), draft.message(), draft.note());
    }

    @PostMapping("/email")
    public MessageResponse email(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access,
            @PathVariable String id,
            @Valid @RequestBody(required = false) EmailDocumentRequest request) {
        documentMailService.emailPortal(access, id, request == null ? EmailDocumentRequest.DEFAULTS : request);
        return new MessageResponse(true, "The portal link was emailed.");
    }

    @PostMapping("/whatsapp")
    public WhatsAppShareResponse whatsApp(
            @Requires(Permission.EDIT_RECORDS) BusinessAccess access, @PathVariable String id) {
        return portalService.whatsApp(access, id);
    }

    @DeleteMapping
    public MessageResponse revoke(@Requires(Permission.EDIT_RECORDS) BusinessAccess access, @PathVariable String id) {
        portalService.revoke(access, id);
        return new MessageResponse(true, "The portal link was turned off.");
    }
}
