package com.tom.payment.routinemanager.dto;

import java.util.UUID;

/**
 * What the Profile screen shows (specs/F006 contracts/settings-api.md "GET /profile").
 * Never includes the password hash or routines.
 */
public record UserProfileView(UUID id, String username, String email, Plan plan) {

    /** Subscription plan. Everyone is on the Free plan in F006; nothing is stored for it. */
    public record Plan(String code, String name) {
        public static final Plan FREE = new Plan("FREE", "Free plan");
    }
}
