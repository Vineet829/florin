package com.florin.auth;

import com.florin.business.BusinessService;
import com.florin.user.User;
import com.florin.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnverifiedSignUps {

    static final Duration KEPT = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final BusinessService businessService;
    private final Clock clock;

    public UnverifiedSignUps(UserRepository userRepository, BusinessService businessService, Clock clock) {
        this.userRepository = userRepository;
        this.businessService = businessService;
        this.clock = clock;
    }

    @Transactional
    public void remove(User user) {
        businessService.deleteOwnedBy(user);
        userRepository.delete(user);
        userRepository.flush();
    }

    @Transactional
    public int removeExpired() {
        List<User> expired = userRepository.findUnverifiedCreatedBefore(clock.instant().minus(KEPT));
        expired.forEach(this::remove);
        return expired.size();
    }
}
