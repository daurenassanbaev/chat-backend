package com.messenger.chatgateway.messaging.dto;

public record MessageDto (
        String toUserId,
        String text
) {}

