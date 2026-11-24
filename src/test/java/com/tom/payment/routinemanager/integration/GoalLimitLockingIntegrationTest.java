package com.tom.payment.routinemanager.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.tom.payment.routinemanager.repository.PersonalGoalRepository;
import com.tom.payment.routinemanager.support.BaseComponentTest;
import com.tom.payment.routinemanager.support.RequiresPostgres;
import com.tom.payment.routinemanager.support.TestUser;

/**
 * Connection: PostgreSQL row locking for the 3-goal limit (F013 research R2). Creating a goal
 * locks the user's row (UserRepository.findLockedById) before counting, so saves sent at the
 * same moment from several tabs never make a 4th goal. Runs with -PtestDb=postgres.
 */
@RequiresPostgres
class GoalLimitLockingIntegrationTest extends BaseComponentTest {

    private static final int TABS = 6;

    @Autowired
    private PersonalGoalRepository goalRepository;

    @Test
    void concurrentCreatesNeverExceedThreeGoals() throws Exception {
        TestUser user = newUser("goal-lock");
        create(user, "Goal 1");
        create(user, "Goal 2");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(TABS);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < TABS; i++) {
                String goal = "Parallel " + i;
                results.add(pool.submit(() -> {
                    start.await();
                    return create(user, goal);
                }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, statuses.stream().filter(status -> status == 201).count(), "statuses " + statuses);
        assertEquals(TABS - 1, statuses.stream().filter(status -> status == 409).count(), "statuses " + statuses);
        assertEquals(3, goalRepository.countByUserId(user.id()));
    }

    private int create(TestUser user, String goal) throws Exception {
        return mvc.perform(as(user, post("/api/v1/users/" + user.id() + "/goals"), Map.of("goal", goal)))
                .andReturn().getResponse().getStatus();
    }
}
