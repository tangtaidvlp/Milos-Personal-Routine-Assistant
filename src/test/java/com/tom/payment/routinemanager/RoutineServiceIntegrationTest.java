package com.tom.payment.routinemanager;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.tom.payment.routinemanager.model.DailyRoutine;
import com.tom.payment.routinemanager.model.DailyTask;
import com.tom.payment.routinemanager.model.DefaultRoutine;
import com.tom.payment.routinemanager.model.RoutineTaskTemplate;
import com.tom.payment.routinemanager.model.User;
import com.tom.payment.routinemanager.repository.DailyRoutineRepository;
import com.tom.payment.routinemanager.repository.DailyTaskRepository;
import com.tom.payment.routinemanager.repository.DefaultRoutineRepository;
import com.tom.payment.routinemanager.repository.RoutineTaskTemplateRepository;
import com.tom.payment.routinemanager.repository.UserRepository;
import com.tom.payment.routinemanager.service.DailyRoutineService;
import com.tom.payment.routinemanager.service.DefaultRoutineService;
import com.tom.payment.routinemanager.service.UserService;

@SpringBootTest
@Transactional
public class RoutineServiceIntegrationTest {

    @Autowired
    private DefaultRoutineService routineService;

    @Autowired
    private DailyRoutineService dailyRoutineService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DefaultRoutineRepository defaultRoutineRepository;

    @Autowired
    private DailyRoutineRepository dailyRoutineRepository;

    @Autowired
    private RoutineTaskTemplateRepository routineTaskTemplateRepository;

    @Autowired
    private DailyTaskRepository dailyTaskRepository;

    @Test
    public void testCreateDailyRoutineFromDefault() {
        // 1. Setup User (now automatically creates Default Routine)
        User user = new User("testuser", "test@example.com");
        user = userService.createUserAndDefaultRoutine(user);

        // 2. Update Default Routine (instead of creating a new one)
        DefaultRoutine defaultRoutine = new DefaultRoutine();
        defaultRoutine.setName("Workday Routine");

        RoutineTaskTemplate task1 = new RoutineTaskTemplate();
        task1.setName("Morning Coffee");
        task1.setStartTime(LocalTime.of(8, 0));
        task1.setDurationMinutes(15);

        defaultRoutine.setTasks(Collections.singletonList(task1));
        routineService.createDefaultRoutine(user.getId(), defaultRoutine);

        // 3. Trigger Daily Routine creation
        LocalDate today = LocalDate.now();
        DailyRoutine dailyRoutine = dailyRoutineService.getOrCreateDailyRoutine(user.getId(), today);

        // 4. Assertions
        assertNotNull(dailyRoutine);
        assertEquals(today, dailyRoutine.getDate());
        assertEquals(user.getId(), dailyRoutine.getUser().getId());
        assertEquals(1, dailyRoutine.getTasks().size());
        
        DailyTask dailyTask = dailyRoutine.getTasks().get(0);
        assertEquals("Morning Coffee", dailyTask.getName());
        assertEquals(LocalTime.of(8, 0), dailyTask.getStartTime());
        assertFalse(dailyTask.isCompleted());
    }

    @Test
    public void testUpdateDefaultRoutine() {
        User user = new User("updateuser", "update@example.com");
        user = userService.createUserAndDefaultRoutine(user);

        DefaultRoutine existing = defaultRoutineRepository.findByUser(user).orElseThrow();
        UUID routineId = existing.getId();

        DefaultRoutine update = new DefaultRoutine();
        update.setName("Updated Name");
        RoutineTaskTemplate task = new RoutineTaskTemplate();
        task.setName("New Task");
        update.setTasks(Collections.singletonList(task));

        routineService.updateDefaultRoutine(routineId, update);

        DefaultRoutine updated = defaultRoutineRepository.findById(routineId).orElseThrow();
        assertEquals("Updated Name", updated.getName());
        assertEquals(1, updated.getTasks().size());
        assertEquals("New Task", updated.getTasks().get(0).getName());
        assertEquals(updated, updated.getTasks().get(0).getDefaultRoutine());
    }

    @Test
    public void testBulkDefaultTasksOperations() {
        User user = new User("bulkuser", "bulk@example.com");
        user = userService.createUserAndDefaultRoutine(user);
        DefaultRoutine routine = defaultRoutineRepository.findByUser(user).orElseThrow();

        // 1. Add bulk default tasks
        RoutineTaskTemplate task1 = new RoutineTaskTemplate();
        task1.setName("Task A");
        task1.setStartTime(LocalTime.of(9, 0));
        task1.setDurationMinutes(30);

        RoutineTaskTemplate task2 = new RoutineTaskTemplate();
        task2.setName("Task B");
        task2.setStartTime(LocalTime.of(10, 0));
        task2.setDurationMinutes(60);

        List<RoutineTaskTemplate> added = routineService.addDefaultTasks(routine.getId(), Arrays.asList(task1, task2));
        assertEquals(2, added.size());

        DefaultRoutine fetchedRoutine = defaultRoutineRepository.findById(routine.getId()).orElseThrow();
        assertEquals(2, fetchedRoutine.getTasks().size());

        // 2. Update bulk default tasks
        RoutineTaskTemplate t1 = fetchedRoutine.getTasks().get(0);
        RoutineTaskTemplate t2 = fetchedRoutine.getTasks().get(1);
        t1.setName("Task A Updated");
        t2.setName("Task B Updated");

        List<RoutineTaskTemplate> updated = routineService.updateDefaultTasks(Arrays.asList(t1, t2));
        assertEquals(2, updated.size());

        DefaultRoutine fetchedRoutine2 = defaultRoutineRepository.findById(routine.getId()).orElseThrow();
        assertTrue(fetchedRoutine2.getTasks().stream().anyMatch(t -> t.getName().equals("Task A Updated")));
        assertTrue(fetchedRoutine2.getTasks().stream().anyMatch(t -> t.getName().equals("Task B Updated")));

        // 3. Delete bulk default tasks
        routineService.deleteDefaultTasks(Arrays.asList(t1.getId(), t2.getId()));
        DefaultRoutine fetchedRoutine3 = defaultRoutineRepository.findById(routine.getId()).orElseThrow();
        assertEquals(0, fetchedRoutine3.getTasks().size());
    }

    @Test
    public void testAddingOverlappingDefaultTaskShiftsConflictingTasks() {
        User user = new User("overlapuser", "overlap@example.com");
        user = userService.createUserAndDefaultRoutine(user);

        DefaultRoutine defaultRoutine = new DefaultRoutine();
        defaultRoutine.setName("Overlap Routine");

        RoutineTaskTemplate existingTask = new RoutineTaskTemplate();
        existingTask.setName("Existing Task");
        existingTask.setStartTime(LocalTime.of(9, 0));
        existingTask.setDurationMinutes(60);

        defaultRoutine.setTasks(Collections.singletonList(existingTask));
        routineService.createDefaultRoutine(user.getId(), defaultRoutine);

        RoutineTaskTemplate newTask = new RoutineTaskTemplate();
        newTask.setName("Inserted Task");
        newTask.setStartTime(LocalTime.of(9, 30));
        newTask.setDurationMinutes(30);

        routineService.addDefaultTasks(defaultRoutineRepository.findByUser(user).orElseThrow().getId(), Collections.singletonList(newTask));

        DefaultRoutine fetchedRoutine = defaultRoutineRepository.findByUser(user).orElseThrow();
        RoutineTaskTemplate shiftedTask = fetchedRoutine.getTasks().stream()
                .filter(task -> "Existing Task".equals(task.getName()))
                .findFirst()
                .orElseThrow();

        assertEquals(LocalTime.of(10, 0), shiftedTask.getStartTime());
        assertEquals(60, shiftedTask.getDurationMinutes());
    }

    @Test
    public void testBulkDailyTasksOperations() {
        User user = new User("bulkdailyuser", "bulkdaily@example.com");
        user = userService.createUserAndDefaultRoutine(user);

        LocalDate today = LocalDate.now();
        DailyRoutine dailyRoutine = dailyRoutineService.getOrCreateDailyRoutine(user.getId(), today);

        // 1. Add bulk daily tasks
        DailyTask task1 = new DailyTask();
        task1.setName("Daily Task A");
        task1.setStartTime(LocalTime.of(9, 0));
        task1.setDurationMinutes(30);
        task1.setCompleted(false);

        DailyTask task2 = new DailyTask();
        task2.setName("Daily Task B");
        task2.setStartTime(LocalTime.of(10, 0));
        task2.setDurationMinutes(60);
        task2.setCompleted(true);

        List<DailyTask> added = dailyRoutineService.addDailyTasks(dailyRoutine.getId(), Arrays.asList(task1, task2));
        assertEquals(2, added.size());

        DailyRoutine fetchedRoutine = dailyRoutineRepository.findById(dailyRoutine.getId()).orElseThrow();
        assertEquals(2, fetchedRoutine.getTasks().size());

        // 2. Update bulk daily tasks
        DailyTask dt1 = fetchedRoutine.getTasks().get(0);
        DailyTask dt2 = fetchedRoutine.getTasks().get(1);
        dt1.setName("Daily Task A Updated");
        dt1.setCompleted(true);
        dt2.setName("Daily Task B Updated");
        dt2.setCompleted(false);

        List<DailyTask> updated = dailyRoutineService.updateDailyTasks(Arrays.asList(dt1, dt2));
        assertEquals(2, updated.size());

        DailyRoutine fetchedRoutine2 = dailyRoutineRepository.findById(dailyRoutine.getId()).orElseThrow();
        DailyTask updatedDt1 = fetchedRoutine2.getTasks().stream().filter(t -> t.getName().equals("Daily Task A Updated")).findFirst().orElseThrow();
        assertTrue(updatedDt1.isCompleted());
        DailyTask updatedDt2 = fetchedRoutine2.getTasks().stream().filter(t -> t.getName().equals("Daily Task B Updated")).findFirst().orElseThrow();
        assertFalse(updatedDt2.isCompleted());

        // 3. Delete bulk daily tasks
        dailyRoutineService.deleteDailyTasks(Arrays.asList(dt1.getId(), dt2.getId()));
        DailyRoutine fetchedRoutine3 = dailyRoutineRepository.findById(dailyRoutine.getId()).orElseThrow();
        assertEquals(0, fetchedRoutine3.getTasks().size());
    }
}
