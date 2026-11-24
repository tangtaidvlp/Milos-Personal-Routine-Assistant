package com.tom.payment.routinemanager.service;

import com.tom.payment.routinemanager.funcs.ChatKind;

/**
 * Which Buddy call a prompt is for (specs/F015 data-model): chats have the proposeGoal tool (F013),
 * and the kind sets how long replies may be.
 */
public enum BuddyCall {

    /** Default routine chat, including onboarding: planning. */
    DEFAULT_ROUTINE_CHAT(true, ChatKind.DEEP),
    /** The Today chat: quick day-to-day help. */
    DAILY_CHAT(true, ChatKind.DAY_TO_DAY),
    REFLECTION_CHAT(true, ChatKind.DEEP),
    /** The Personal Target chat (F013 US4): coaching on goals; it can only propose goals. */
    PERSONAL_TARGET_CHAT(true, ChatKind.DEEP),
    /** Buddy's reflection note: no tools. */
    REFLECTION_FEEDBACK(false, ChatKind.DEEP);

    private final boolean inChat;
    private final ChatKind kind;

    BuddyCall(boolean inChat, ChatKind kind) {
        this.inChat = inChat;
        this.kind = kind;
    }

    public boolean inChat() {
        return inChat;
    }

    public ChatKind kind() {
        return kind;
    }
}
