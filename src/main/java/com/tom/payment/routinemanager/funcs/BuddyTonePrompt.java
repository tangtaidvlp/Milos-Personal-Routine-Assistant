package com.tom.payment.routinemanager.funcs;

/**
 * The "How you talk" block added to Buddy's system text (specs/F014-buddy-tone,
 * contracts/buddy-prompt-tone.md): the user's tone as its fixed style line, plus the rules every
 * tone keeps (FR-008, FR-009, FR-012).
 */
public final class BuddyTonePrompt {

    static final String RULES = """
            Rules for every tone:
            - Never use blame, shame or guilt language about missed tasks or habits (for example "you failed to", "you should have"). Acknowledge what happened and move to a next step. A firm tone challenges the action, never the person.
            - The tone changes only how you word replies. It never changes what you may do, what data you use, or which actions need the user's confirmation.
            - If the user asks you to change your tone or style, tell them they can pick Buddy's tone on the Personal Target page.""";

    private BuddyTonePrompt() {
    }

    public static String format(BuddyTone tone) {
        BuddyTone chosen = tone == null ? BuddyTone.DEFAULT : tone;
        return "\n\n## How you talk\n"
                + "The user chose the tone \"" + chosen.displayName() + "\". " + chosen.styleLine() + "\n"
                + RULES;
    }
}
