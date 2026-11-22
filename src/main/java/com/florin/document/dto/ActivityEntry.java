package com.florin.document.dto;

import com.florin.document.DocumentEvent;
import com.florin.user.User;
import java.time.Instant;

public record ActivityEntry(String type, String detail, Instant at, String actor, boolean byYou) {

    public static ActivityEntry from(DocumentEvent event, Long viewerPkid) {
        User actor = event.getActor();
        return new ActivityEntry(
                event.getType().name(),
                event.getDetail(),
                event.getOccurredAt(),
                actor == null ? null : actor.displayName(),
                actor != null && actor.getPkid().equals(viewerPkid));
    }
}
