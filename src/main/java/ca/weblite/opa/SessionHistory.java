package ca.weblite.opa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the session history (session/history.json) of an OPA archive.
 */
public class SessionHistory {

    private String opaVersion = OpaManifest.OPA_VERSION;
    private String sessionId;
    private String createdAt;
    private String updatedAt;
    private final List<Message> messages = new ArrayList<>();

    public SessionHistory() {}

    public SessionHistory(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getOpaVersion() { return opaVersion; }
    public SessionHistory setOpaVersion(String opaVersion) { this.opaVersion = opaVersion; return this; }

    public String getSessionId() { return sessionId; }
    public SessionHistory setSessionId(String sessionId) { this.sessionId = sessionId; return this; }

    public String getCreatedAt() { return createdAt; }
    public SessionHistory setCreatedAt(String createdAt) { this.createdAt = createdAt; return this; }

    public String getUpdatedAt() { return updatedAt; }
    public SessionHistory setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; return this; }

    public List<Message> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public SessionHistory addMessage(Message message) {
        messages.add(message);
        return this;
    }

    public SessionHistory clearMessages() {
        messages.clear();
        return this;
    }
}
