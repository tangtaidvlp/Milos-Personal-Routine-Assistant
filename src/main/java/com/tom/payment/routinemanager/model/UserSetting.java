package com.tom.payment.routinemanager.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One stored UI preference of a user (specs/F006 data-model §1). A missing row means
 * "use the default". The user is referenced by id only, so the {@link User} JSON is unchanged.
 * DDL: db/F006-user-settings.sql.
 */
@Entity
@Table(name = "user_setting")
@IdClass(UserSettingId.class)
@Getter
@Setter
@NoArgsConstructor
public class UserSetting {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "setting_key", length = 64, nullable = false)
    private String settingKey;

    @Column(name = "setting_value", length = 255, nullable = false)
    private String settingValue;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UserSetting(UUID userId, String settingKey, String settingValue, OffsetDateTime updatedAt) {
        this.userId = userId;
        this.settingKey = settingKey;
        this.settingValue = settingValue;
        this.updatedAt = updatedAt;
    }
}
