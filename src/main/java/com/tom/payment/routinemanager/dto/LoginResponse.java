package com.tom.payment.routinemanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
    private int expiresIn;
    private UserInfo user;

    @Data
    @AllArgsConstructor
    public static class UserInfo {
        private String id;
        private String email;
        private String name;
        private boolean onboardCompleted;
    }
}
