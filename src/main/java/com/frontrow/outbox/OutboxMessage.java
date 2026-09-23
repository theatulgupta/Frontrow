package com.frontrow.outbox;

import java.util.UUID;

public record OutboxMessage(UUID id, String eventType, UUID aggregateId, String payload) {
}
