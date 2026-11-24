package com.tom.payment.routinemanager.dto;

/**
 * Body of POST/PUT /api/v1/users/{userId}/goals (specs/F013 contracts/goals-api.md).
 * Validated by {@code GoalRules} in PersonalGoalService (research R3).
 */
public record GoalRequest(String goal, String howToHelp) {
}
