package com.florin.business;

import static org.assertj.core.api.Assertions.assertThat;

import com.florin.IntegrationTest;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class EndpointPermissionsTest extends IntegrationTest {

    private static final Set<String> BUSINESS_PACKAGES = Set.of(
            "com.florin.customer", "com.florin.document", "com.florin.item",
            "com.florin.report", "com.florin.export", "com.florin.settings", "com.florin.expense",
            "com.florin.notification", "com.florin.upload");

    private static final Set<String> MEMBER_OWN_WRITES =
            Set.of("POST /api/v1/notification/read", "POST /api/v1/business/leave");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapping;

    @Test
    void everyEndpointDeclaresItsPermission() {
        List<String> missing = new ArrayList<>();
        List<String> readOnlyWrites = new ArrayList<>();
        List<String> accountPrincipals = new ArrayList<>();
        mapping.getHandlerMethods().forEach((info, method) -> {
            String endpoint = describe(info);
            for (MethodParameter parameter : method.getMethodParameters()) {
                if (BusinessAccess.class.equals(parameter.getParameterType())) {
                    Requires requires = parameter.getParameterAnnotation(Requires.class);
                    if (requires == null) {
                        missing.add(endpoint);
                    } else if (writes(info)
                            && EnumSet.of(Permission.VIEW_RECORDS, Permission.EXPORT_LISTS).contains(requires.value())
                            && !MEMBER_OWN_WRITES.contains(endpoint)) {
                        readOnlyWrites.add(endpoint);
                    }
                }
                if (parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                        && BUSINESS_PACKAGES.contains(method.getBeanType().getPackageName())) {
                    accountPrincipals.add(endpoint);
                }
            }
        });

        assertThat(missing).as("endpoints without @Requires").isEmpty();
        assertThat(readOnlyWrites).as("writes that only need to read").isEmpty();
        assertThat(accountPrincipals).as("business endpoints taking the account").isEmpty();
    }

    private static boolean writes(RequestMappingInfo info) {
        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
        return !methods.isEmpty() && !methods.contains(RequestMethod.GET);
    }

    private static String describe(RequestMappingInfo info) {
        return info.getMethodsCondition().getMethods().stream().findFirst().map(Enum::name).orElse("ANY")
                + " " + String.join(",", info.getPatternValues());
    }
}
