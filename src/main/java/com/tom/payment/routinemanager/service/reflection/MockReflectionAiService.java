package com.tom.payment.routinemanager.service.reflection;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.tom.payment.routinemanager.dto.ReflectionComparison;
import com.tom.payment.routinemanager.dto.ReflectionView;
import com.tom.payment.routinemanager.service.chat.ChatContext;

/** Used when AI is off: a deterministic, kind note and an echo chat. */
@Service
@ConditionalOnProperty(name = "spring.ai.active", havingValue = "false", matchIfMissing = true)
public class MockReflectionAiService implements ReflectionAiService {

    @Override
    public String feedback(ReflectionView view) {
        ReflectionComparison c = view.comparison();
        ReflectionComparison.Counts n = c.counts();
        StringBuilder note = new StringBuilder();

        if (n.asPlanned() + n.changed() > 0) {
            note.append("Nice work today: you showed up for ")
                    .append(n.asPlanned() + n.changed()).append(" of your planned tasks");
            note.append(n.asPlanned() > 0 ? ", and " + n.asPlanned() + " went just as planned. " : ". ");
        } else if (n.unplanned() > 0) {
            note.append("Today took its own path, and you still made time for ")
                    .append(n.unplanned()).append(" thing(s) that mattered. ");
        } else {
            note.append("Taking a moment to look back on your day is already a great step. ");
        }

        List<String> ideas = new ArrayList<>();
        if (n.skipped() > 0) {
            ideas.add("you could try giving one skipped task a smaller, easier slot tomorrow");
        }
        if (c.actualMinutes() > c.plannedMinutes() + 30) {
            ideas.add("you could leave a little extra buffer between tasks");
        }
        if (n.unplanned() > 0 && ideas.size() < 2) {
            ideas.add("you could keep a small open slot for the unexpected");
        }
        if (!ideas.isEmpty()) {
            note.append("If you like, ").append(String.join(", and ", ideas.subList(0, Math.min(2, ideas.size())))).append(". ");
        }
        note.append("Every day is a fresh start. Keep it up!");
        return note.toString();
    }

    @Override
    public String chat(UUID userId, LocalDate date, ReflectionView view, ChatContext context, String userMessage) {
        return "Mock reflection response for user " + userId + " (" + date + "): " + userMessage;
    }
}
