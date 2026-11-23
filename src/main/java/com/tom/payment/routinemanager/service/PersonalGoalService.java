package com.tom.payment.routinemanager.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.tom.payment.routinemanager.dto.GoalRequest;
import com.tom.payment.routinemanager.funcs.GoalRules;
import com.tom.payment.routinemanager.model.PersonalGoal;
import com.tom.payment.routinemanager.repository.PersonalGoalRepository;
import com.tom.payment.routinemanager.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * A user's personal goals (specs/F013-personal-goals, contracts/goals-api.md). At most
 * {@link GoalRules#MAX_GOALS} per user: {@link #create} counts under a user-row lock, so two
 * saves at once can't make a 4th goal (research R2). Every lookup is scoped by the owner.
 */
@Service
@RequiredArgsConstructor
public class PersonalGoalService {

    public static final String NOT_FOUND = "Goal not found";

    private final PersonalGoalRepository repository;
    private final UserRepository userRepository;
    private final Clock clock;

    /** The user's goals, oldest first. */
    @Transactional(readOnly = true)
    public List<PersonalGoal> list(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtAscIdAsc(userId);
    }

    /** The user's goal with this id, if it is theirs. */
    @Transactional(readOnly = true)
    public Optional<PersonalGoal> find(UUID userId, UUID goalId) {
        return repository.findByIdAndUserId(goalId, userId);
    }

    @Transactional
    public PersonalGoal create(UUID userId, GoalRequest request) {
        GoalRules.Normalized value = normalize(request);
        userRepository.findLockedById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (repository.countByUserId(userId) >= GoalRules.MAX_GOALS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, GoalRules.LIMIT_REACHED);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        PersonalGoal goal = new PersonalGoal();
        goal.setUserId(userId);
        goal.setGoal(value.goal());
        goal.setHowToHelp(value.howToHelp());
        goal.setCreatedAt(now);
        goal.setUpdatedAt(now);
        return repository.save(goal);
    }

    @Transactional
    public PersonalGoal update(UUID userId, UUID goalId, GoalRequest request) {
        GoalRules.Normalized value = normalize(request);
        PersonalGoal goal = repository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND));
        goal.setGoal(value.goal());
        goal.setHowToHelp(value.howToHelp());
        goal.setUpdatedAt(OffsetDateTime.now(clock));
        return repository.save(goal);
    }

    @Transactional
    public void delete(UUID userId, UUID goalId) {
        PersonalGoal goal = repository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND));
        repository.delete(goal);
    }

    private static GoalRules.Normalized normalize(GoalRequest request) {
        try {
            return GoalRules.normalize(request == null ? null : request.goal(),
                    request == null ? null : request.howToHelp());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
