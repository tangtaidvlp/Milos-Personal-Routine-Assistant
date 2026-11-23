package com.tom.payment.routinemanager.support;

import java.util.UUID;

/** A user registered and signed in through the public API, with a real access token. */
public record TestUser(UUID id, String email, String username, String password, String accessToken) {
}
