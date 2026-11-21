package com.tom.payment.routinemanager.service.reflection;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.tom.payment.routinemanager.dto.ReflectionComparison;
import com.tom.payment.routinemanager.dto.ReflectionComparison.Item;
import com.tom.payment.routinemanager.dto.ReflectionComparison.Status;
import com.tom.payment.routinemanager.dto.ReflectionComparison.TaskRef;
import com.tom.payment.routinemanager.model.DailyTask;
import com.tom.payment.routinemanager.model.ReflectionTask;

/**
 * Groups a day's tasks into as planned / changed / skipped / unplanned
 * (specs/F007-reflection research R3). Pure: no repository access.
 */
@Service
public class ReflectionComparisonService {

    private static final Comparator<LocalTime> NULLS_LAST = Comparator.nullsLast(Comparator.naturalOrder());

    public ReflectionComparison compare(List<DailyTask> planned, List<ReflectionTask> actual) {
        List<DailyTask> plan = planned == null ? List.of() : planned;
        List<ReflectionTask> reality = actual == null ? List.of() : actual;

        Map<UUID, DailyTask> planById = new LinkedHashMap<>();
        for (DailyTask task : plan) {
            if (task.getId() != null) {
                planById.put(task.getId(), task);
            }
        }

        // Reality in start order, so the first task linking to a planned task is its match.
        List<ReflectionTask> realityByStart = new ArrayList<>(reality);
        realityByStart.sort(Comparator.comparing(ReflectionTask::getStartTime, NULLS_LAST));

        List<Item> items = new ArrayList<>();
        Set<UUID> matched = new HashSet<>();
        for (ReflectionTask r : realityByStart) {
            UUID link = r.getPlannedTaskId();
            DailyTask p = link == null ? null : planById.get(link);
            if (p == null || !matched.add(link)) {
                items.add(new Item(Status.UNPLANNED, null, ref(r)));
                continue;
            }
            boolean same = Objects.equals(p.getStartTime(), r.getStartTime())
                    && p.getDurationMinutes() == r.getDurationMinutes();
            items.add(new Item(same ? Status.AS_PLANNED : Status.CHANGED, ref(p), ref(r)));
        }
        for (DailyTask p : plan) {
            if (p.getId() == null || !matched.contains(p.getId())) {
                items.add(new Item(Status.SKIPPED, ref(p), null));
            }
        }

        items.sort(Comparator.comparing(ReflectionComparisonService::sortTime, NULLS_LAST));

        int plannedMinutes = plan.stream().mapToInt(DailyTask::getDurationMinutes).sum();
        int actualMinutes = reality.stream().mapToInt(ReflectionTask::getDurationMinutes).sum();
        return new ReflectionComparison(plannedMinutes, actualMinutes, new ReflectionComparison.Counts(
                count(items, Status.AS_PLANNED),
                count(items, Status.CHANGED),
                count(items, Status.SKIPPED),
                count(items, Status.UNPLANNED)), items);
    }

    public static TaskRef ref(DailyTask task) {
        return new TaskRef(task.getId(), task.getName(), task.getStartTime(), task.getDurationMinutes());
    }

    private static TaskRef ref(ReflectionTask task) {
        return new TaskRef(task.getId(), task.getName(), task.getStartTime(), task.getDurationMinutes());
    }

    private static LocalTime sortTime(Item item) {
        return item.actual() != null ? item.actual().startTime() : item.planned().startTime();
    }

    private static int count(List<Item> items, Status status) {
        return (int) items.stream().filter(i -> i.status() == status).count();
    }
}
