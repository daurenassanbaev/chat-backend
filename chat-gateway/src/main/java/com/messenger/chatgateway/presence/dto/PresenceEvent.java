package com.messenger.chatgateway.presence.dto;

import lombok.Builder;

@Builder
public record PresenceEvent(
        String userId,
        String status
){}
