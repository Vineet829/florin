package com.florin.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.florin.IntegrationTest;
import com.florin.auth.dto.ConfirmIdentityRequest;
import com.florin.user.User;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class InvitationRaceIntegrationTest extends IntegrationTest {

    private static final Pattern LINK = Pattern.compile("/invite/([A-Za-z0-9_-]{43})");

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private TeamService teamService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    private Business business;
    private BusinessAccess access;
    private User invitee;
    private String session;
    private String id;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        User founder = withAccountPassword(createUser());
        business = businessOf(founder);
        access = new BusinessAccess(business.getPkid(), business.getId(), BusinessRole.OWNER, founder);
        clearInvocations(mailSender);
        invitee = createUser();
        session = bearer(invitee);
        id = JsonPath.read(send(post("/api/v1/business/invitations"), bearer(founder, business), """
                        {"email": "%s", "role": "MEMBER"}
                        """.formatted(invitee.getEmail()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$._id");
        token = links(1).get(0);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String bearer, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private List<String> links(int count) {
        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(5000).times(count)).send(mail.capture());
        return mail.getAllValues().stream().map(message -> {
            Matcher link = LINK.matcher(message.getText());
            assertThat(link.find()).isTrue();
            return link.group(1);
        }).toList();
    }

    private int acceptWhile(String link, Runnable meanwhile) throws Exception {
        CountDownLatch ran = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try {
            Future<?> holder = pool.submit(() -> transaction.executeWithoutResult(tx -> {
                meanwhile.run();
                ran.countDown();
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
            if (!ran.await(30, TimeUnit.SECONDS)) {
                holder.get(1, TimeUnit.SECONDS);
                throw new AssertionError("The change never ran");
            }
            Future<Integer> accepted = pool.submit(() -> send(
                            post("/api/v1/invitations/" + link + "/accept"), session, "")
                    .andReturn()
                    .getResponse()
                    .getStatus());
            awaitLockWait(accepted);
            release.countDown();
            holder.get(30, TimeUnit.SECONDS);
            return accepted.get(30, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private void awaitLockWait(Future<?> request) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline && !request.isDone()) {
            Integer waiting = jdbc.queryForObject(
                    "select count(*) from pg_stat_activity where datname = current_database()"
                            + " and wait_event_type = 'Lock'",
                    Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(20);
        }
    }

    @Test
    void acceptWhileResent() throws Exception {
        int accepted = acceptWhile(token, () -> invitationService.resend(access, id));

        assertThat(accepted).isEqualTo(404);
        String fresh = links(2).get(1);
        send(post("/api/v1/invitations/" + fresh + "/accept"), session, "").andExpect(status().isOk());
    }

    @Test
    void acceptWhileRevoked() throws Exception {
        int accepted = acceptWhile(token, () -> invitationService.revoke(access, id));

        assertThat(accepted).isEqualTo(409);
        assertThat(businessMemberRepository.findByBusinessPkidAndUserPkid(business.getPkid(), invitee.getPkid()))
                .isEmpty();
    }

    @Test
    void acceptWhileBusinessDeleted() throws Exception {
        int accepted = acceptWhile(
                token, () -> teamService.delete(access, new ConfirmIdentityRequest(ACCOUNT_PASSWORD, null)));

        assertThat(accepted).isEqualTo(404);
    }
}
