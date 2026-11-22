package com.florin.business;

import com.florin.auth.session.CurrentSession;
import com.florin.business.dto.BusinessListResponse;
import com.florin.business.dto.BusinessSummaryResponse;
import com.florin.business.dto.CreateBusinessRequest;
import com.florin.business.dto.UseBusinessRequest;
import com.florin.user.User;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/businesses")
public class BusinessesController {

    private final ActiveBusinesses activeBusinesses;
    private final BusinessService businessService;

    public BusinessesController(ActiveBusinesses activeBusinesses, BusinessService businessService) {
        this.activeBusinesses = activeBusinesses;
        this.businessService = businessService;
    }

    @GetMapping
    public BusinessListResponse list(@AuthenticationPrincipal User principal) {
        return new BusinessListResponse(
                true,
                activeBusinesses.list(principal, session(), CurrentSession.businessPkid().orElse(null)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BusinessSummaryResponse create(
            @AuthenticationPrincipal User principal, @Valid @RequestBody CreateBusinessRequest request) {
        Business business = businessService.create(principal, request.name());
        activeBusinesses.use(principal, session(), business.getId());
        return BusinessSummaryResponse.from(
                business, BusinessRole.OWNER, true, 0, activeBusinesses.timeZone(business.getPkid()));
    }

    @PutMapping("/active")
    public BusinessSummaryResponse use(
            @AuthenticationPrincipal User principal, @Valid @RequestBody UseBusinessRequest request) {
        BusinessAccess access = activeBusinesses.use(principal, session(), request.businessId());
        Business business = businessService.require(access.businessPkid());
        return BusinessSummaryResponse.from(
                business,
                access.role(),
                true,
                activeBusinesses.unread(principal.getPkid(), access.businessPkid()),
                activeBusinesses.timeZone(access.businessPkid()));
    }

    private static UUID session() {
        return CurrentSession.id().orElse(null);
    }
}
