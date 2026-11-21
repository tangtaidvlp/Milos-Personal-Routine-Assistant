package com.tom.payment.routinemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tom.payment.routinemanager.dto.ReflectionComparison;
import com.tom.payment.routinemanager.dto.ReflectionComparison.Status;
import com.tom.payment.routinemanager.model.DailyTask;
import com.tom.payment.routinemanager.model.ReflectionTask;
import com.tom.payment.routinemanager.service.reflection.ReflectionComparisonService;

/** F007 research R3: plan vs reality grouping. */
class ReflectionComparisonServiceTest {

    private final ReflectionComparisonService service = new ReflectionComparisonService();

    @Test
    void groupsEveryTaskIntoExactlyOneStatus() {
        DailyTask gym = planned("Gym", "07:00", 60);
        DailyTask work = planned("Work", "09:00", 240);
        DailyTask read = planned("Read", "21:00", 30);

        ReflectionComparison c = service.compare(List.of(gym, work, read), List.of(
                actual(gym.getId(), "Gym", "07:00", 60),      // as planned
                actual(work.getId(), "Work", "09:30", 240),   // changed (moved)
                actual(null, "Coffee with Sam", "16:00", 45)  // unplanned
        ));                                                    // read → skipped

        assertEquals(new ReflectionComparison.Counts(1, 1, 1, 1), c.counts());
        assertEquals(330, c.plannedMinutes());
        assertEquals(345, c.actualMinutes());
        assertEquals(List.of(Status.AS_PLANNED, Status.CHANGED, Status.UNPLANNED, Status.SKIPPED),
                c.items().stream().map(ReflectionComparison.Item::status).toList());
    }

    @Test
    void renameKeepsTheMatch() {
        DailyTask gym = planned("Gym", "07:00", 60);
        ReflectionComparison same = service.compare(List.of(gym), List.of(actual(gym.getId(), "Yoga", "07:00", 60)));
        assertEquals(Status.AS_PLANNED, same.items().get(0).status());

        ReflectionComparison shorter = service.compare(List.of(gym), List.of(actual(gym.getId(), "Short yoga", "07:00", 30)));
        assertEquals(Status.CHANGED, shorter.items().get(0).status());
        assertEquals("Gym", shorter.items().get(0).planned().name());
        assertEquals("Short yoga", shorter.items().get(0).actual().name());
    }

    @Test
    void danglingLinkCountsAsUnplanned() {
        ReflectionComparison c = service.compare(List.of(), List.of(actual(UUID.randomUUID(), "Gym", "07:00", 60)));
        assertEquals(new ReflectionComparison.Counts(0, 0, 0, 1), c.counts());
        assertNull(c.items().get(0).planned());
    }

    @Test
    void secondTaskLinkingToSamePlanIsUnplanned() {
        DailyTask gym = planned("Gym", "07:00", 60);
        ReflectionComparison c = service.compare(List.of(gym), List.of(
                actual(gym.getId(), "Gym", "07:00", 60),
                actual(gym.getId(), "Gym again", "18:00", 30)));
        assertEquals(new ReflectionComparison.Counts(1, 0, 0, 1), c.counts());
    }

    @Test
    void emptyEverything() {
        ReflectionComparison c = service.compare(List.of(), List.of());
        assertEquals(new ReflectionComparison.Counts(0, 0, 0, 0), c.counts());
        assertEquals(0, c.items().size());
    }

    @Test
    void allDeletedMeansAllSkipped() {
        DailyTask a = planned("A", "08:00", 30);
        DailyTask b = planned("B", "09:00", 30);
        ReflectionComparison c = service.compare(List.of(a, b), List.of());
        assertEquals(new ReflectionComparison.Counts(0, 0, 2, 0), c.counts());
        assertEquals(60, c.plannedMinutes());
        assertEquals(0, c.actualMinutes());
    }

    private static DailyTask planned(String name, String start, int minutes) {
        DailyTask t = new DailyTask();
        t.setId(UUID.randomUUID());
        t.setName(name);
        t.setStartTime(LocalTime.parse(start));
        t.setDurationMinutes(minutes);
        return t;
    }

    private static ReflectionTask actual(UUID plannedId, String name, String start, int minutes) {
        ReflectionTask t = new ReflectionTask();
        t.setId(UUID.randomUUID());
        t.setPlannedTaskId(plannedId);
        t.setName(name);
        t.setStartTime(LocalTime.parse(start));
        t.setDurationMinutes(minutes);
        return t;
    }
}
