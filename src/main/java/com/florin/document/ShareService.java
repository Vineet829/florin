package com.florin.document;

import com.florin.business.Business;
import com.florin.business.BusinessAccess;
import com.florin.business.Permission;
import com.florin.common.ApiException;
import com.florin.common.SecretBox;
import com.florin.common.SecretTokens;
import com.florin.config.AppProperties;
import com.florin.document.dto.PublicDocumentResponse;
import com.florin.document.dto.ReportPaymentRequest;
import com.florin.document.dto.ShareLinkResponse;
import com.florin.document.dto.ShareStatusResponse;
import com.florin.document.dto.WhatsAppShareResponse;
import com.florin.mail.Placeholders;
import com.florin.notification.NotificationService;
import com.florin.notification.NotificationType;
import com.florin.settings.SettingsService;
import com.florin.tax.TaxRegime;
import com.florin.user.User;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShareService {

    private static final Duration VIEW_GAP = Duration.ofHours(1);
    private static final String WHATSAPP_MESSAGE = "Hello {client}, here is {document} from {business}: {link}";

    private final DocumentRepository documentRepository;
    private final ShareLinkRepository shareLinkRepository;
    private final DocumentActivity activity;
    private final SettingsService settingsService;
    private final NotificationService notificationService;
    private final PublicDocuments publicDocuments;
    private final SecretBox secretBox;
    private final AppProperties properties;
    private final Clock clock;

    public ShareService(
            DocumentRepository documentRepository,
            ShareLinkRepository shareLinkRepository,
            DocumentActivity activity,
            SettingsService settingsService,
            NotificationService notificationService,
            PublicDocuments publicDocuments,
            SecretBox secretBox,
            AppProperties properties,
            Clock clock) {
        this.documentRepository = documentRepository;
        this.shareLinkRepository = shareLinkRepository;
        this.activity = activity;
        this.settingsService = settingsService;
        this.notificationService = notificationService;
        this.publicDocuments = publicDocuments;
        this.secretBox = secretBox;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ShareLinkResponse create(BusinessAccess access, String id) {
        Document document = requireInBusiness(access, id, true);
        DocumentService.requireIssued(document, "Issue this draft before sharing it");
        DocumentService.requireNotVoid(document, "A void document can't be shared");
        return newLink(document, access.user(), null);
    }

    private ShareLinkResponse newLink(Document document, User actor, String detail) {
        return newLink(document, actor, detail, SecretTokens.generate());
    }

    private ShareLinkResponse newLink(Document document, User actor, String detail, String token) {
        Instant now = Instant.now(clock);
        shareLinkRepository.findByDocumentAndRevokedAtIsNull(document).ifPresent(active -> {
            active.setRevokedAt(now);
            shareLinkRepository.saveAndFlush(active);
        });
        ShareLink link = new ShareLink();
        link.setDocument(document);
        link.setTokenHash(SecretTokens.hash(token));
        if (secretBox.available()) {
            link.setTokenSealed(secretBox.seal(token.getBytes(StandardCharsets.UTF_8)));
        }
        link.setCreatedAt(now);
        shareLinkRepository.save(link);
        activity.record(document, DocumentEventType.SHARED, detail, actor);
        return new ShareLinkResponse(true, address(token), now, link.getTokenSealed() != null);
    }

    public record EmailLink(String url, String replaces) {}

    @Transactional(readOnly = true)
    public EmailLink peekForEmail(BusinessAccess access, UUID documentId) {
        Document document = requireInBusiness(access, documentId.toString());
        return shareLinkRepository.findByDocumentAndRevokedAtIsNull(document)
                .map(link -> recovered(link)
                        .map(url -> new EmailLink(url, null))
                        .orElseGet(() -> new EmailLink(null, PdfService.DATE.format(link.getCreatedAt().atZone(
                                ZoneId.of(settingsService.forBusiness(access.businessPkid()).getTimeZone()))))))
                .orElseGet(() -> new EmailLink(null, null));
    }

    @Transactional
    public Optional<SentLink> linkForEmail(BusinessAccess access, UUID documentId, boolean mayReplace, User actor) {
        return linkToSend(access, documentId, mayReplace, actor, "for an email", true);
    }

    public record SentLink(String url, boolean replaced, String token) {}

    @Transactional
    public void keep(BusinessAccess access, UUID documentId, SentLink link, User actor) {
        if (link.token() != null) {
            newLink(lockInBusiness(access, documentId), actor, "for an email", link.token());
        }
    }

    private Optional<SentLink> linkToSend(
            BusinessAccess access, UUID documentId, boolean mayReplace, User actor, String detail, boolean later) {
        Document document = lockInBusiness(access, documentId);
        if (document.isDraft() || document.isVoid()) {
            return Optional.empty();
        }
        Optional<ShareLink> active = shareLinkRepository.findByDocumentAndRevokedAtIsNull(document);
        Optional<String> current = active.flatMap(this::recovered);
        if (current.isPresent() || (active.isPresent() && !mayReplace)) {
            return current.map(url -> new SentLink(url, false, null));
        }
        if (active.isPresent() && later) {
            String token = SecretTokens.generate();
            return Optional.of(new SentLink(address(token), true, token));
        }
        return Optional.of(new SentLink(newLink(document, actor, detail).url(), active.isPresent(), null));
    }

    private Document lockInBusiness(BusinessAccess access, UUID documentId) {
        return documentRepository.findForUpdateByExternalId(documentId)
                .filter(found -> found.getBusiness().getPkid().equals(access.businessPkid()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That document does not exist"));
    }

    @Transactional
    public WhatsAppShareResponse whatsApp(BusinessAccess access, String id) {
        Document document = requireInBusiness(access, id);
        DocumentService.requireIssued(document, "Issue this draft before sharing it");
        DocumentService.requireNotVoid(document, "A void document can't be shared");
        SentLink link = linkToSend(access, document.getId(), true, access.user(), "for WhatsApp", false).orElseThrow();
        Business business = document.getBusiness();
        CustomerSnapshot client = Objects.requireNonNullElseGet(document.getCustomerSnapshot(), CustomerSnapshot::new);
        boolean indian =
                WhatsAppLink.inIndia(business, settingsService.forBusiness(business.getPkid()).getTaxRegime());
        String text = Placeholders.fill(WHATSAPP_MESSAGE, Map.of(
                "client", client.getName() == null || client.getName().isBlank() ? "there" : client.getName(),
                "document", PublicDocuments.title(document),
                "business", PublicDocuments.sellerParty(document, business).name(),
                "link", link.url()));
        return new WhatsAppShareResponse(true, WhatsAppLink.of(client.getPhoneNumber(), indian, text), link.replaced());
    }

    @Transactional(readOnly = true)
    public ShareStatusResponse status(BusinessAccess access, String id) {
        return shareLinkRepository.findByDocumentAndRevokedAtIsNull(requireInBusiness(access, id))
                .map(link -> new ShareStatusResponse(
                        true,
                        true,
                        link.getCreatedAt(),
                        link.getLastViewedAt(),
                        link.getViewCount(),
                        access.can(Permission.EDIT_RECORDS) ? recovered(link).orElse(null) : null))
                .orElseGet(() -> new ShareStatusResponse(true, false, null, null, 0, null));
    }

    Optional<String> recovered(ShareLink link) {
        if (link.getTokenSealed() == null || !secretBox.available()) {
            return Optional.empty();
        }
        try {
            return Optional.of(address(new String(secretBox.open(link.getTokenSealed()), StandardCharsets.UTF_8)));
        } catch (IllegalStateException unreadable) {
            return Optional.empty();
        }
    }

    private String address(String token) {
        return properties.site().clientUrl() + "/p/" + token;
    }

    @Transactional
    public void revoke(BusinessAccess access, String id) {
        Document document = requireInBusiness(access, id);
        ShareLink link = shareLinkRepository.findByDocumentAndRevokedAtIsNull(document)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This document has no share link"));
        link.setRevokedAt(Instant.now(clock));
        activity.record(document, DocumentEventType.SHARE_REVOKED, null, access.user());
    }

    @Transactional
    public PublicDocumentResponse view(String token) {
        ShareLink link = requireLink(token);
        Instant now = Instant.now(clock);
        if (link.getLastViewedAt() == null || link.getLastViewedAt().isBefore(now.minus(VIEW_GAP))) {
            Document document = link.getDocument();
            activity.record(document, DocumentEventType.VIEWED, null, null);
            notificationService.notify(
                    document,
                    NotificationType.DOCUMENT_VIEWED,
                    PublicDocuments.clientName(document) + " viewed " + PublicDocuments.title(document));
        }
        link.setLastViewedAt(now);
        link.setViewCount(link.getViewCount() + 1);
        return publicDocuments.view(link.getDocument());
    }

    @Transactional(readOnly = true)
    public DocumentPdf pdf(String token) {
        return publicDocuments.pdf(requireLink(token).getDocument());
    }

    @Transactional
    public PublicDocumentResponse accept(String token) {
        return publicDocuments.accept(lockedDocument(token));
    }

    @Transactional
    public PublicDocumentResponse decline(String token, String reason) {
        return publicDocuments.decline(lockedDocument(token), reason);
    }

    @Transactional
    public PublicDocumentResponse reportPayment(String token, ReportPaymentRequest request) {
        return publicDocuments.reportPayment(requireLink(token).getDocument(), request);
    }

    private Document requireInBusiness(BusinessAccess access, String id) {
        return requireInBusiness(access, id, false);
    }

    private Document requireInBusiness(BusinessAccess access, String id, boolean lock) {
        UUID externalId;
        try {
            externalId = UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "That document does not exist");
        }
        Document document = (lock
                        ? documentRepository.findForUpdateByExternalId(externalId)
                        : documentRepository.findByExternalId(externalId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That document does not exist"));
        if (!document.getBusiness().getPkid().equals(access.businessPkid())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only share your own documents");
        }
        return document;
    }

    private Document lockedDocument(String token) {
        return documentRepository.findForUpdateByPkid(requireLink(token).getDocument().getPkid())
                .orElseThrow(ShareService::linkGone);
    }

    private ShareLink requireLink(String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            throw linkGone();
        }
        return shareLinkRepository.findByTokenHashAndRevokedAtIsNull(SecretTokens.hash(token))
                .orElseThrow(ShareService::linkGone);
    }

    private static ApiException linkGone() {
        return new ApiException(HttpStatus.NOT_FOUND, "This link doesn't work anymore");
    }

}
