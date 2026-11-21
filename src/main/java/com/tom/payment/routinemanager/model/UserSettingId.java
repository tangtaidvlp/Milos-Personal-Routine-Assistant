package com.tom.payment.routinemanager.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Composite key of {@link UserSetting}: one value per (user, setting key). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSettingId implements Serializable {

    private UUID userId;
    private String settingKey;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserSettingId other)) return false;
        return Objects.equals(userId, other.userId) && Objects.equals(settingKey, other.settingKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, settingKey);
    }
}
