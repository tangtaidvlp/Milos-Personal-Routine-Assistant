package com.tom.payment.routinemanager.funcs;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Buddy's tones (specs/F014-buddy-tone, data-model §2). Each is a fixed preset of directiveness,
 * warmth, strictness and length, put into words as one style line for the prompt
 * (contracts/buddy-prompt-tone.md). The ids are stored in {@code user_setting}: never rename or
 * remove one without a migration. UI copy lives in the frontend {@code utils/buddyTones.ts}.
 */
public enum BuddyTone {

    BALANCED("Balanced",
            "Be friendly and pragmatic: greet briefly, then be concise. Offer one clear recommendation and ask before making big changes. Keep replies short."),
    CHIEF_OF_STAFF("Tactical Chief of Staff",
            "Be crisp, structured and proactive, with zero fluff and no small talk. State a clear plan with times instead of asking; use short lines or a short list."),
    STOIC("Grounded Stoic",
            "Be calm, objective, non-judgmental and steady. Treat setbacks as data, not failure, and suggest a smaller, clear next step. Keep replies short."),
    SOCRATIC_PEER("Socratic Peer",
            "Be curious, analytical and collaborative. Point out patterns you notice and ask one or two thoughtful questions that help the user find the fix. Replies may be a little longer."),
    WARM_ANCHOR("Warm Anchor",
            "Be encouraging, gentle, low-friction and empathetic. Keep the bar low, offer one easy next step, and ask before changing anything. Keep replies short and kind."),
    ACCOUNTABILITY_COACH("High-Accountability Coach",
            "Be direct, challenging, energetic and firm. Give one concrete instruction to act on now and hold the user to it. Keep replies short.");

    public static final BuddyTone DEFAULT = BALANCED;

    private final String displayName;
    private final String styleLine;

    BuddyTone(String displayName, String styleLine) {
        this.displayName = displayName;
        this.styleLine = styleLine;
    }

    public String displayName() {
        return displayName;
    }

    public String styleLine() {
        return styleLine;
    }

    /** All ids, in catalogue order. */
    public static List<String> ids() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }

    /** The tone for a stored or typed id (trimmed, any case); empty when it isn't one. */
    public static Optional<BuddyTone> parse(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String upper = id.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(tone -> tone.name().equals(upper)).findFirst();
    }

    /** A stored value, or {@link #DEFAULT} when there is none or it is unknown (F014 FR-011). */
    public static BuddyTone fromStored(String stored) {
        return parse(stored).orElse(DEFAULT);
    }
}
