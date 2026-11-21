package com.tom.payment.routinemanager.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tom.payment.routinemanager.model.UserSetting;
import com.tom.payment.routinemanager.model.UserSettingId;

public interface UserSettingRepository extends JpaRepository<UserSetting, UserSettingId> {

    List<UserSetting> findByUserId(UUID userId);
}
