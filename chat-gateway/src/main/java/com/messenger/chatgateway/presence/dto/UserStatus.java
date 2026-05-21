package com.messenger.chatgateway.presence.dto;

import lombok.Getter;

@Getter
public enum UserStatus {
    ONLINE("online"),
    OFFLINE("offline");

    private final String value;

    UserStatus(String value) {
        this.value = value;
    }

}