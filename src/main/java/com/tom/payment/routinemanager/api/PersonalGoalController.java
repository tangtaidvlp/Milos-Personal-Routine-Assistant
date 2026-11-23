package com.tom.payment.routinemanager.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tom.payment.routinemanager.config.CurrentUserGuard;
import com.tom.payment.routinemanager.dto.GoalListView;
import com.tom.payment.routinemanager.dto.GoalRequest;
import com.tom.payment.routinemanager.dto.GoalView;
import com.tom.payment.routinemanager.funcs.GoalRules;
import com.tom.payment.routinemanager.service.PersonalGoalService;

import lombok.RequiredArgsConstructor;

/**
 * Personal goals (specs/F013-personal-goals, contracts/goals-api.md).
 * {@link CurrentUserGuard}: 404 for an unknown user, 403 when the signed-in user is someone else.
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/goals")
@RequiredArgsConstructor
public class PersonalGoalController {

    private final CurrentUserGuard currentUserGuard;
    private final PersonalGoalService goalService;

    @GetMapping
    public GoalListView list(@PathVariable UUID userId) {
        currentUserGuard.requireUser(userId);
        return new GoalListView(goalService.list(userId).stream().map(GoalView::of).toList(), GoalRules.MAX_GOALS);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalView create(@PathVariable UUID userId, @RequestBody GoalRequest request) {
        currentUserGuard.requireUser(userId);
        return GoalView.of(goalService.create(userId, request));
    }

    @PutMapping("/{goalId}")
    public GoalView update(@PathVariable UUID userId, @PathVariable UUID goalId, @RequestBody GoalRequest request) {
        currentUserGuard.requireUser(userId);
        return GoalView.of(goalService.update(userId, goalId, request));
    }

    @DeleteMapping("/{goalId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID userId, @PathVariable UUID goalId) {
        currentUserGuard.requireUser(userId);
        goalService.delete(userId, goalId);
    }
}
