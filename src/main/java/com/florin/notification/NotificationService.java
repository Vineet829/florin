package com.florin.notification;

import com.florin.business.BusinessAccess;
import com.florin.business.BusinessMember;
import com.florin.business.BusinessMemberRepository;
import com.florin.business.BusinessRole;
import com.florin.common.ApiException;
import com.florin.document.Document;
import com.florin.notification.dto.NotificationListResponse;
import com.florin.notification.dto.NotificationResponse;
import com.florin.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final int LATEST = 30;
    private static final int MESSAGE_LENGTH = 300;
    private static final Set<BusinessRole> RUNNING_THE_BUSINESS = EnumSet.of(BusinessRole.OWNER, BusinessRole.ADMIN);

    private final NotificationRepository notificationRepository;
    private final BusinessMemberRepository memberRepository;
    private final Clock clock;

    public NotificationService(
            NotificationRepository notificationRepository, BusinessMemberRepository memberRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    @Transactional
    public void notify(Document document, NotificationType type, String message) {
        Long businessPkid = document.getBusiness().getPkid();
        Map<Long, User> recipients = new LinkedHashMap<>();
        for (BusinessMember member : memberRepository.findInRoles(businessPkid, RUNNING_THE_BUSINESS)) {
            recipients.put(member.getUser().getPkid(), member.getUser());
        }
        User author = document.getCreatedBy();
        if (author != null
                && memberRepository.findByBusinessPkidAndUserPkid(businessPkid, author.getPkid()).isPresent()) {
            recipients.putIfAbsent(author.getPkid(), author);
        }
        String text = message.length() > MESSAGE_LENGTH ? message.substring(0, MESSAGE_LENGTH) : message;
        Instant now = Instant.now(clock);
        notificationRepository.saveAll(recipients.values().stream()
                .map(recipient -> {
                    Notification notification = new Notification();
                    notification.setUser(recipient);
                    notification.setBusiness(document.getBusiness());
                    notification.setDocument(document);
                    notification.setType(type);
                    notification.setMessage(text);
                    notification.setCreatedAt(now);
                    return notification;
                })
                .toList());
    }

    @Transactional(readOnly = true)
    public NotificationListResponse latest(BusinessAccess access) {
        return new NotificationListResponse(
                true,
                notificationRepository.countByUserPkidAndBusinessPkidAndReadAtIsNull(
                        access.userPkid(), access.businessPkid()),
                notificationRepository
                        .findByUserPkidAndBusinessPkidOrderByCreatedAtDescPkidDesc(
                                access.userPkid(), access.businessPkid(), PageRequest.of(0, LATEST))
                        .stream()
                        .map(NotificationResponse::from)
                        .toList());
    }

    @Transactional
    public void markRead(BusinessAccess access, String id) {
        Instant now = Instant.now(clock);
        if (id == null || id.isBlank()) {
            notificationRepository.markAllRead(access.userPkid(), access.businessPkid(), now);
            return;
        }
        UUID externalId;
        try {
            externalId = UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            throw notFound();
        }
        Notification notification = notificationRepository
                .findByIdAndUserPkidAndBusinessPkid(externalId, access.userPkid(), access.businessPkid())
                .orElseThrow(NotificationService::notFound);
        if (notification.getReadAt() == null) {
            notification.setReadAt(now);
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Notification not found");
    }
}
