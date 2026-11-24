package com.tom.payment.routinemanager.service;

import java.time.LocalTime;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.tom.payment.routinemanager.funcs.BuddyTonePrompt;
import com.tom.payment.routinemanager.funcs.PersonalGoalPrompt;
import com.tom.payment.routinemanager.funcs.TimeOfDayPrompt;

import lombok.RequiredArgsConstructor;

/**
 * What Buddy knows about the user on every call (epic E001): their goals (F013), the tone they
 * chose (F014) and their part of the day (F015). The text comes from the pure formatters; this
 * service only loads the data, fresh on each call so a change applies from the next message.
 */
@Service
@RequiredArgsConstructor
public class BuddyContextService {

    private final PersonalGoalService goalService;
    private final UserSettingsService settingsService;

    /** @param localTime the user's local time, or null when the client didn't send a valid one */
    public String promptBlock(UUID userId, BuddyCall call, LocalTime localTime) {
        return PersonalGoalPrompt.format(goalService.list(userId), call.inChat())
                + BuddyTonePrompt.format(settingsService.buddyTone(userId))
                + TimeOfDayPrompt.format(localTime, call.kind());
    }
}
