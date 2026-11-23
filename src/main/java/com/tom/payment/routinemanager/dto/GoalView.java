package com.tom.payment.routinemanager.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.tom.payment.routinemanager.model.PersonalGoal;

/** One personal goal as the API returns it (specs/F013 contracts/goals-api.md). */
public record GoalView(UUID id, String goal, String howToHelp, OffsetDateTime createdAt, OffsetDateTime updatedAt) {

    public static GoalView of(PersonalGoal goal) {
        return new GoalView(goal.getId(), goal.getGoal(), goal.getHowToHelp(), goal.getCreatedAt(), goal.getUpdatedAt());
    }
}
