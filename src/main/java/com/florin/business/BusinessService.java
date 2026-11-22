package com.florin.business;

import com.florin.audit.AuditEventType;
import com.florin.audit.AuditLog;
import com.florin.business.dto.BusinessProfileResponse;
import com.florin.business.dto.UpdateBusinessRequest;
import com.florin.common.ApiException;
import com.florin.settings.BusinessSettingsRepository;
import com.florin.tax.Gstin;
import com.florin.tax.TaxRegime;
import com.florin.user.DemoAccountGuard;
import com.florin.user.User;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessService {

    private static final int MOST_OWNED = 10;

    private final BusinessRepository businessRepository;
    private final BusinessMemberRepository memberRepository;
    private final DemoAccountGuard demoAccountGuard;
    private final AuditLog auditLog;
    private final BusinessSettingsRepository settingsRepository;
    private final Clock clock;

    public BusinessService(
            BusinessRepository businessRepository,
            BusinessMemberRepository memberRepository,
            DemoAccountGuard demoAccountGuard,
            AuditLog auditLog,
            BusinessSettingsRepository settingsRepository,
            Clock clock) {
        this.businessRepository = businessRepository;
        this.memberRepository = memberRepository;
        this.demoAccountGuard = demoAccountGuard;
        this.auditLog = auditLog;
        this.settingsRepository = settingsRepository;
        this.clock = clock;
    }

    @Transactional
    public Business createPersonal(User user) {
        return found(user, personalName(user));
    }

    @Transactional
    public Business create(User user, String name) {
        demoAccountGuard.requireNotDemo(user, "starting a business");
        if (name == null || name.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A business needs a name");
        }
        if (businessRepository.findOwnedBy(user.getPkid()).size() >= MOST_OWNED) {
            throw new ApiException(HttpStatus.CONFLICT, "You can own at most " + MOST_OWNED + " businesses");
        }
        Business business = found(user, name.trim());
        auditLog.record(AuditEventType.BUSINESS_CREATED, user, user, null, business.getName());
        return business;
    }

    private Business found(User user, String name) {
        Business business = new Business();
        business.setName(name);
        business.setEmail(user.getEmail());
        businessRepository.save(business);
        BusinessMember owner = new BusinessMember();
        owner.setBusiness(business);
        owner.setUser(user);
        owner.setRole(BusinessRole.OWNER);
        owner.setJoinedAt(clock.instant());
        memberRepository.save(owner);
        return business;
    }

    static String personalName(User user) {
        String fullName = (value(user.getFirstName()) + " " + value(user.getLastName())).trim();
        return fullName.isEmpty() ? user.getUsername() : fullName;
    }

    @Transactional(readOnly = true)
    public Business ownedBy(Long userPkid) {
        return businessRepository.findOwnedBy(userPkid).stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found"));
    }

    @Transactional(readOnly = true)
    public Business require(Long businessPkid) {
        return businessRepository
                .findById(businessPkid)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found"));
    }

    @Transactional(readOnly = true)
    public User owner(Long businessPkid) {
        return memberRepository.findOwner(businessPkid)
                .map(BusinessMember::getUser)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This business has no owner"));
    }

    @Transactional(readOnly = true)
    public BusinessAccess ownerAccess(Long businessPkid) {
        BusinessMember owner = memberRepository.findOwner(businessPkid)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This business has no owner"));
        return new BusinessAccess(businessPkid, require(businessPkid).getId(), BusinessRole.OWNER, owner.getUser());
    }

    @Transactional(readOnly = true)
    public BusinessProfileResponse profile(BusinessAccess access) {
        return BusinessProfileResponse.from(require(access), access.role());
    }

    @Transactional
    public BusinessProfileResponse updateProfile(BusinessAccess access, UpdateBusinessRequest request) {
        demoAccountGuard.requireNotDemo(access.user(), "changing the business's details");
        Business business = businessRepository.findForUpdate(access.businessPkid())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found"));
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "A business needs a name");
            }
            business.setName(request.name().trim());
        }
        update(request.legalName(), business::setLegalName);
        update(request.email(), business::setEmail);
        update(request.phoneNumber(), business::setPhoneNumber);
        update(request.address(), business::setAddress);
        update(request.city(), business::setCity);
        update(request.state(), business::setState);
        update(request.postalCode(), business::setPostalCode);
        update(request.country(), business::setCountry);
        update(request.logo(), business::setLogo);
        if (request.taxId() != null) {
            business.setTaxId(taxId(access.businessPkid(), request.taxId()));
        }
        return BusinessProfileResponse.from(business, access.role());
    }

    @Transactional(readOnly = true)
    public List<String> sharedOwnedBy(Long userPkid) {
        return businessRepository.findSharedNamesOwnedBy(userPkid);
    }

    @Transactional
    public void deleteOwnedBy(User user) {
        businessRepository.deleteAll(businessRepository.findOwnedBy(user.getPkid()));
    }

    private Business require(BusinessAccess access) {
        return require(access.businessPkid());
    }

    private String taxId(Long businessPkid, String value) {
        boolean gst = settingsRepository.findByBusinessPkid(businessPkid)
                .map(settings -> settings.getTaxRegime() == TaxRegime.GST_INDIA)
                .orElse(false);
        if (!gst) {
            return value.isBlank() ? null : value.trim();
        }
        String gstin = value.trim().toUpperCase(Locale.ROOT);
        if (!Gstin.isValid(gstin)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "In GST mode your tax ID must be a valid GSTIN");
        }
        return gstin;
    }

    private static void update(String value, Consumer<String> setter) {
        if (value != null) {
            setter.accept(value.isBlank() ? null : value.trim());
        }
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
