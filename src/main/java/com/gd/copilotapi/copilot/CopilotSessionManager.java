package com.gd.copilotapi.copilot;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class CopilotSessionManager {

    private final Map<String, CopilotSession> sessions = new ConcurrentHashMap<>();

    public CopilotSession create(String model) {
        CopilotSession session = new CopilotSession("session-" + UUID.randomUUID(), model, Instant.now().getEpochSecond());
        sessions.put(session.id(), session);
        return session;
    }

    public CopilotSession get(String id) {
        return sessions.get(id);
    }
}
