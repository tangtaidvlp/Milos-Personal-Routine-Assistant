package com.tom.payment.routinemanager.service.reflection;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.tom.payment.routinemanager.dto.ReflectionComparison;
import com.tom.payment.routinemanager.dto.ReflectionFeedbackView;
import com.tom.payment.routinemanager.dto.ReflectionTaskInput;
import com.tom.payment.routinemanager.dto.ReflectionView;
import com.tom.payment.routinemanager.model.DailyReflection;
import com.tom.payment.routinemanager.model.DailyRoutine;
import com.tom.payment.routinemanager.model.DailyTask;
import com.tom.payment.routinemanager.model.ReflectionTask;
import com.tom.payment.routinemanager.model.User;
import com.tom.payment.routinemanager.repository.DailyReflectionRepository;
import com.tom.payment.routinemanager.repository.DailyRoutineRepository;
import com.tom.payment.routinemanager.service.TaskColorService;
import com.tom.payment.routinemanager.funcs.DayRules;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Daily reflections (specs/.done.F007-reflection). Reads the day's plan but never
 * writes it: no call here creates or changes a DailyRoutine/DailyTask (FR-005/006).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReflectionService {

    static final int MAX_DURATION_MINUTES = 1440;

    private final DailyReflectionRepository reflectionRepository;
    private final DailyRoutineRepository dailyRoutineRepository;
    private final ReflectionComparisonService comparisonService;
    private final TaskColorService taskColorService;
    private final TransactionTemplate tx;
    private final Clock clock;

    // ---------- reads ----------

    /** Read only: never creates a reflection or a plan. */
    public ReflectionView getView(User user, LocalDate date) {
        return tx.execute(status -> reflectionRepository.findByUser_IdAndReflectionDate(user.getId(), date)
                .map(r -> toView(user, date, r))
                .orElseGet(() -> toView(user, date, null)));
    }

    public boolean exists(User user, LocalDate date) {
        return reflectionRepository.findByUser_IdAndReflectionDate(user.getId(), date).isPresent();
    }

    // ---------- create ----------

    /** Create-or-get. The first call copies the day's plan once (FR-003/004). */
    public ReflectionView ensure(User user, LocalDate date) {
        requireNotFuture(date);
        if (reflectionRepository.findByUser_IdAndReflectionDate(user.getId(), date).isEmpty()) {
            try {
                tx.executeWithoutResult(status -> reflectionRepository.saveAndFlush(copyOfPlan(user, date)));
                log.info("Created reflection for user {} on {}", user.getId(), date);
            } catch (DataIntegrityViolationException race) {
                // Another request created it first (unique user + date).
                log.info("Reflection for user {} on {} was created concurrently", user.getId(), date);
            }
        }
        return getView(user, date);
    }

    private DailyReflection copyOfPlan(User user, LocalDate date) {
        DailyReflection reflection = new DailyReflection();
        reflection.setUser(user);
        reflection.setReflectionDate(date);
        Optional<DailyRoutine> plan = dailyRoutineRepository.findByUserAndDate(user, date);
        plan.ifPresent(p -> {
            reflection.setColorCursor(p.getColorCursor());
            for (DailyTask planned : p.getTasks()) {
                ReflectionTask copy = new ReflectionTask();
                copy.setPlannedTaskId(planned.getId());
                copy.setName(planned.getName());
                copy.setDescription(planned.getDescription());
                copy.setStartTime(planned.getStartTime());
                copy.setDurationMinutes(planned.getDurationMinutes());
                copy.setColor(planned.getColor());
                copy.setReflection(reflection);
                reflection.getTasks().add(copy);
            }
        });
        return reflection;
    }

    // ---------- task edits ----------

    /** New tasks are always unplanned. Never shifts overlaps (research R6). */
    public ReflectionView addTasks(User user, LocalDate date, List<ReflectionTaskInput> inputs) {
        requireNotFuture(date);
        requireNonEmpty(inputs);
        return tx.execute(status -> {
            DailyReflection reflection = requireReflection(user, date);
            List<ReflectionTask> created = new ArrayList<>();
            for (ReflectionTaskInput input : inputs) {
                ReflectionTask task = new ReflectionTask();
                task.setName(requireName(input.name()));
                task.setDescription(blankToNull(input.description()));
                task.setStartTime(requireStart(input.startTime()));
                task.setDurationMinutes(requireDuration(input.durationMinutes()));
                task.setColor(input.color());
                created.add(task);
            }
            reflection.setColorCursor(taskColorService.assign(reflection.getColorCursor(), created,
                    ReflectionTask::getName, ReflectionTask::getColor, ReflectionTask::setColor));
            for (ReflectionTask task : created) {
                task.setReflection(reflection);
                reflection.getTasks().add(task);
            }
            reflection.touchContent();
            reflectionRepository.flush(); // managed: flush cascades persist/orphan removal
            return toView(user, date, reflection);
        });
    }

    /** Every id must be in this reflection, else 404 and nothing is written. A null color keeps it. */
    public ReflectionView updateTasks(User user, LocalDate date, List<ReflectionTaskInput> inputs) {
        requireNotFuture(date);
        requireNonEmpty(inputs);
        return tx.execute(status -> {
            DailyReflection reflection = requireReflection(user, date);
            Map<UUID, ReflectionTask> byId = byId(reflection);
            for (ReflectionTaskInput input : inputs) {
                if (input.id() == null || !byId.containsKey(input.id())) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reflection task not found: " + input.id());
                }
                requireName(input.name());
                requireStart(input.startTime());
                requireDuration(input.durationMinutes());
                if (input.color() != null) {
                    taskColorService.requireValid(input.color());
                }
            }
            for (ReflectionTaskInput input : inputs) {
                ReflectionTask task = byId.get(input.id());
                task.setName(input.name().trim());
                task.setDescription(blankToNull(input.description()));
                task.setStartTime(input.startTime());
                task.setDurationMinutes(input.durationMinutes());
                if (input.color() != null) {
                    task.setColor(input.color());
                }
            }
            reflection.touchContent();
            reflectionRepository.flush(); // managed: flush cascades persist/orphan removal
            return toView(user, date, reflection);
        });
    }

    public ReflectionView deleteTasks(User user, LocalDate date, Collection<UUID> taskIds) {
        requireNotFuture(date);
        if (taskIds == null || taskIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "taskIds must not be empty");
        }
        return tx.execute(status -> {
            DailyReflection reflection = requireReflection(user, date);
            Map<UUID, ReflectionTask> byId = byId(reflection);
            for (UUID id : taskIds) {
                if (!byId.containsKey(id)) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reflection task not found: " + id);
                }
            }
            reflection.getTasks().removeIf(t -> new HashSet<>(taskIds).contains(t.getId()));
            reflection.touchContent();
            reflectionRepository.flush(); // managed: flush cascades persist/orphan removal
            return toView(user, date, reflection);
        });
    }

    // ---------- feedback ----------

    /** Stores Buddy's note; {@code basedOn} is when the note's input was read (see ReflectionFeedbackService). */
    public ReflectionView saveFeedback(User user, LocalDate date, String note, Instant basedOn) {
        return tx.execute(status -> {
            DailyReflection reflection = requireReflection(user, date);
            reflection.setFeedbackText(note.trim());
            reflection.setFeedbackAt(basedOn);
            reflectionRepository.flush();
            return toView(user, date, reflection);
        });
    }

    // ---------- helpers ----------

    private ReflectionView toView(User user, LocalDate date, DailyReflection reflection) {
        List<DailyTask> plan = dailyRoutineRepository.findByUserAndDate(user, date)
                .map(DailyRoutine::getTasks)
                .map(ArrayList::new)
                .orElseGet(ArrayList::new);
        plan.sort((a, b) -> compareTimes(a.getStartTime(), b.getStartTime()));
        List<ReflectionComparison.TaskRef> plannedRefs = plan.stream().map(ReflectionComparisonService::ref).toList();

        if (reflection == null) {
            return new ReflectionView(false, null, date, List.of(), plannedRefs,
                    comparisonService.compare(plan, List.of()), null, null);
        }
        List<ReflectionTask> tasks = new ArrayList<>(reflection.getTasks());
        tasks.sort((a, b) -> compareTimes(a.getStartTime(), b.getStartTime()));
        ReflectionFeedbackView feedback = reflection.getFeedbackText() == null ? null
                : new ReflectionFeedbackView(reflection.getFeedbackText(), reflection.getFeedbackAt(), reflection.isFeedbackStale());
        return new ReflectionView(true, reflection.getId(), date, tasks, plannedRefs,
                comparisonService.compare(plan, tasks), feedback,
                taskColorService.nextColor(reflection.getColorCursor()));
    }

    private DailyReflection requireReflection(User user, LocalDate date) {
        return reflectionRepository.findByUser_IdAndReflectionDate(user.getId(), date)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reflection not found"));
    }

    private static Map<UUID, ReflectionTask> byId(DailyReflection reflection) {
        return reflection.getTasks().stream().collect(Collectors.toMap(ReflectionTask::getId, Function.identity()));
    }

    void requireNotFuture(LocalDate date) {
        if (date == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date is required");
        }
        if (DayRules.isFuture(date, LocalDate.now(clock))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can reflect once the day has happened");
        }
    }

    private static void requireNonEmpty(List<?> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one task is required");
        }
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task name must not be blank");
        }
        return name.trim();
    }

    private static LocalTime requireStart(LocalTime start) {
        if (start == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startTime is required");
        }
        return start;
    }

    private static int requireDuration(Integer minutes) {
        if (minutes == null || minutes < 1 || minutes > MAX_DURATION_MINUTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "durationMinutes must be between 1 and 1440");
        }
        return minutes;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int compareTimes(LocalTime a, LocalTime b) {
        if (a == null) return b == null ? 0 : 1;
        if (b == null) return -1;
        return a.compareTo(b);
    }
}
