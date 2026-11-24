package com.tom.payment.routinemanager.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.tom.payment.routinemanager.dto.GoalRequest;
import com.tom.payment.routinemanager.funcs.GoalReview;
import com.tom.payment.routinemanager.funcs.GoalRules;
import com.tom.payment.routinemanager.model.PersonalGoal;
import com.tom.payment.routinemanager.repository.PersonalGoalRepository;
import com.tom.payment.routinemanager.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A user's personal goals (specs/F013-personal-goals, contracts/goals-api.md). At most
 * {@link GoalRules#MAX_GOALS} per user: {@link #create} counts under a user-row lock, so two
 * saves at once can't make a 4th goal (research R2). Every lookup is scoped by the owner. New and
 * changed goals are checked by Buddy first ({@link GoalReviewer}, US5, research R12).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalGoalService {

    public static final String NOT_FOUND = "Goal not found";
    public static final String REVIEW_UNAVAILABLE = "Buddy couldn't check your goal right now. Please try again in a moment.";

    private final PersonalGoalRepository repository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final GoalReviewer reviewer;
    private final TransactionTemplate transactions;

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

    /**
     * Creates a goal once Buddy accepts it (US5). Text rules and the limit come first, so they never
     * cost an AI call; the review runs outside any transaction, so no row lock is held while Buddy
     * answers; the limit is then checked again under the user-row lock.
     */
    public GoalSaveResult create(UUID userId, GoalRequest request) {
        GoalRules.Normalized value = normalize(request);
        requireRoom(userId);
        GoalReview review = review(userId, value);
        if (!review.accepted()) {
            return GoalSaveResult.refused(review);
        }
        return GoalSaveResult.saved(transactions.execute(status -> insert(userId, value)));
    }

    /** Changes a goal once Buddy accepts the new text (US5). A save that changes nothing isn't reviewed. */
    public GoalSaveResult update(UUID userId, UUID goalId, GoalRequest request) {
        GoalRules.Normalized value = normalize(request);
        PersonalGoal current = requireOwned(userId, goalId);
        if (!value.equals(new GoalRules.Normalized(current.getGoal(), current.getHowToHelp()))) {
            GoalReview review = review(userId, value);
            if (!review.accepted()) {
                return GoalSaveResult.refused(review);
            }
        }
        return GoalSaveResult.saved(transactions.execute(status -> {
            PersonalGoal goal = requireOwned(userId, goalId);
            goal.setGoal(value.goal());
            goal.setHowToHelp(value.howToHelp());
            goal.setUpdatedAt(OffsetDateTime.now(clock));
            return repository.save(goal);
        }));
    }

    private PersonalGoal insert(UUID userId, GoalRules.Normalized value) {
        userRepository.findLockedById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        requireRoom(userId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        PersonalGoal goal = new PersonalGoal();
        goal.setUserId(userId);
        goal.setGoal(value.goal());
        goal.setHowToHelp(value.howToHelp());
        goal.setCreatedAt(now);
        goal.setUpdatedAt(now);
        return repository.save(goal);
    }

    private void requireRoom(UUID userId) {
        if (repository.countByUserId(userId) >= GoalRules.MAX_GOALS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, GoalRules.LIMIT_REACHED);
        }
    }

    private PersonalGoal requireOwned(UUID userId, UUID goalId) {
        return repository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND));
    }

    /** Buddy's check; no answer is a 503 and nothing is saved (FR-024). */
    private GoalReview review(UUID userId, GoalRules.Normalized value) {
        GoalReview review;
        try {
            review = reviewer.review(value);
        } catch (RuntimeException e) {
            log.warn("Goal review failed for user {}", userId, e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, REVIEW_UNAVAILABLE);
        }
        if (review == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, REVIEW_UNAVAILABLE);
        }
        return review;
    }

    @Transactional
    public void delete(UUID userId, UUID goalId) {
        repository.delete(requireOwned(userId, goalId));
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
