package com.openprompt.opa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Represents a message in the OPA session history.
 */
public class Message {

    private String id;
    private MessageRole role;
    private String textContent;
    private List<ContentBlock> contentBlocks;
    private String timestamp;
    private Map<String, Object> metadata;

    public Message(MessageRole role, String content) {
        this.role = role;
        this.textContent = content;
    }

    public Message(MessageRole role, List<ContentBlock> contentBlocks) {
        this.role = role;
        this.contentBlocks = new ArrayList<>(contentBlocks);
    }

    public String getId() { return id; }
    public Message setId(String id) { this.id = id; return this; }

    public MessageRole getRole() { return role; }

    public boolean isMultiContent() {
        return contentBlocks != null;
    }

    public String getTextContent() { return textContent; }

    public List<ContentBlock> getContentBlocks() {
        return contentBlocks != null ? Collections.unmodifiableList(contentBlocks) : null;
    }

    public String getTimestamp() { return timestamp; }
    public Message setTimestamp(String timestamp) { this.timestamp = timestamp; return this; }

    public Map<String, Object> getMetadata() { return metadata; }
    public Message setMetadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
}
