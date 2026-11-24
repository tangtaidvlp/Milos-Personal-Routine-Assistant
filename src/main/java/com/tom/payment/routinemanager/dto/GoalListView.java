package com.tom.payment.routinemanager.dto;

import java.util.List;

/** A user's goals, oldest first, and how many they may have (specs/F013 contracts/goals-api.md). */
public record GoalListView(List<GoalView> goals, int limit) {
}
