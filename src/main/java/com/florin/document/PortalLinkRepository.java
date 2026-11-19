package com.florin.document;

import com.florin.customer.Customer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortalLinkRepository extends JpaRepository<PortalLink, Long> {

    Optional<PortalLink> findByCustomerAndRevokedAtIsNull(Customer customer);

    Optional<PortalLink> findByTokenHashAndRevokedAtIsNull(String tokenHash);
}
