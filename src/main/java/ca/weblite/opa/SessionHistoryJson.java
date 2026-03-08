package ca.weblite.opa;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Handles serialization and deserialization of SessionHistory to/from JSON.
 */
class SessionHistoryJson {

    @SuppressWarnings("unchecked")
    static SessionHistory parse(InputStream in) throws IOException {
        String json = readAll(in);
        Map<String, Object> root = (Map<String, Object>) Json.parse(json);

        SessionHistory history = new SessionHistory();
        history.setOpaVersion((String) root.get("opa_version"));
        history.setSessionId((String) root.get("session_id"));
        history.setCreatedAt((String) root.get("created_at"));
        history.setUpdatedAt((String) root.get("updated_at"));

        List<Object> messages = (List<Object>) root.get("messages");
        if (messages != null) {
            for (Object msgObj : messages) {
                Map<String, Object> msgMap = (Map<String, Object>) msgObj;
                Message message = parseMessage(msgMap);
                history.addMessage(message);
            }
        }

        return history;
    }

    @SuppressWarnings("unchecked")
    private static Message parseMessage(Map<String, Object> msgMap) {
        MessageRole role = MessageRole.fromValue((String) msgMap.get("role"));
        Object contentRaw = msgMap.get("content");

        Message message;
        if (contentRaw instanceof String) {
            message = new Message(role, (String) contentRaw);
        } else if (contentRaw instanceof List) {
            List<ContentBlock> blocks = new ArrayList<>();
            for (Object blockObj : (List<Object>) contentRaw) {
                blocks.add(parseContentBlock((Map<String, Object>) blockObj));
            }
            message = new Message(role, blocks);
        } else {
            message = new Message(role, contentRaw != null ? contentRaw.toString() : "");
        }

        if (msgMap.containsKey("id")) {
            message.setId(String.valueOf(msgMap.get("id")));
        }
        if (msgMap.containsKey("timestamp")) {
            message.setTimestamp((String) msgMap.get("timestamp"));
        }
        if (msgMap.containsKey("metadata")) {
            message.setMetadata((Map<String, Object>) msgMap.get("metadata"));
        }

        return message;
    }

    @SuppressWarnings("unchecked")
    private static ContentBlock parseContentBlock(Map<String, Object> map) {
        String type = (String) map.get("type");
        ContentBlock block;
        switch (type) {
            case "text":
                block = ContentBlock.text((String) map.get("text"));
                break;
            case "image":
                block = ContentBlock.image(getSourcePath(map));
                break;
            case "file":
                block = ContentBlock.file(getSourcePath(map));
                break;
            case "tool_use":
                block = ContentBlock.toolUse(
                        (String) map.get("id"),
                        (String) map.get("name"),
                        (Map<String, Object>) map.get("input")
                );
                break;
            case "tool_result":
                block = ContentBlock.toolResult(
                        (String) map.get("tool_use_id"),
                        map.get("content")
                );
                break;
            default:
                // Unknown block type - create as text with the type preserved
                block = ContentBlock.text("");
                block.setType(type);
                break;
        }
        return block;
    }

    @SuppressWarnings("unchecked")
    private static String getSourcePath(Map<String, Object> map) {
        Map<String, Object> source = (Map<String, Object>) map.get("source");
        return source != null ? (String) source.get("path") : null;
    }

    static void write(SessionHistory history, OutputStream out) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("opa_version", history.getOpaVersion());
        root.put("session_id", history.getSessionId());
        if (history.getCreatedAt() != null) {
            root.put("created_at", history.getCreatedAt());
        }
        if (history.getUpdatedAt() != null) {
            root.put("updated_at", history.getUpdatedAt());
        }

        List<Object> messages = new ArrayList<>();
        for (Message msg : history.getMessages()) {
            messages.add(serializeMessage(msg));
        }
        root.put("messages", messages);

        out.write(Json.write(root).getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Object> serializeMessage(Message msg) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (msg.getId() != null) {
            map.put("id", msg.getId());
        }
        map.put("role", msg.getRole().getValue());

        if (msg.isMultiContent()) {
            List<Object> blocks = new ArrayList<>();
            for (ContentBlock block : msg.getContentBlocks()) {
                blocks.add(serializeContentBlock(block));
            }
            map.put("content", blocks);
        } else {
            map.put("content", msg.getTextContent());
        }

        if (msg.getTimestamp() != null) {
            map.put("timestamp", msg.getTimestamp());
        }
        if (msg.getMetadata() != null && !msg.getMetadata().isEmpty()) {
            map.put("metadata", msg.getMetadata());
        }
        return map;
    }

    private static Map<String, Object> serializeContentBlock(ContentBlock block) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", block.getType());

        switch (block.getType()) {
            case "text":
                map.put("text", block.getText());
                break;
            case "image":
            case "file":
                map.put("source", block.getSource());
                break;
            case "tool_use":
                map.put("id", block.getId());
                map.put("name", block.getName());
                map.put("input", block.getInput() != null ? block.getInput() : new LinkedHashMap<>());
                break;
            case "tool_result":
                map.put("tool_use_id", block.getToolUseId());
                map.put("content", block.getContent());
                break;
        }
        return map;
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        return baos.toString("UTF-8");
    }
}
