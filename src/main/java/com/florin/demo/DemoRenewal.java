package com.florin.demo;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.demo", name = {"seed", "reset"}, havingValue = "true")
public class DemoRenewal {

    private final DemoSeedRunner runner;

    public DemoRenewal(DemoSeedRunner runner) {
        this.runner = runner;
    }

    @Scheduled(cron = "${app.jobs.cron:0 0 7 * * *}", zone = "${app.jobs.zone:UTC}")
    @SchedulerLock(name = "demo-renewal", lockAtMostFor = "PT1H", lockAtLeastFor = "PT5M")
    public void run() {
        runner.renew();
    }
}
