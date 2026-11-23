package com.tom.payment.routinemanager.funcs;

/** How long Buddy's replies may be in a kind of conversation (specs/F015, FR-008/FR-009). */
public enum ChatKind {

    DAY_TO_DAY("This is a quick day-to-day chat: keep your text to at most 3 sentences unless the user asks for more detail. "
            + "A plan or task list they asked for doesn't count."),
    DEEP("This is a planning or reflection conversation: a longer coaching reply is fine when it helps.");

    private final String lengthRule;

    ChatKind(String lengthRule) {
        this.lengthRule = lengthRule;
    }

    public String lengthRule() {
        return lengthRule;
    }
}
