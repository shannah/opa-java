package ca.weblite.opa;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a content block in a session history message.
 * Supports types: text, image, file, tool_use, tool_result.
 */
public class ContentBlock {

    private String type;
    private String text;
    private Map<String, String> source;
    private String id;
    private String name;
    private Map<String, Object> input;
    private String toolUseId;
    private Object content;

    private ContentBlock() {}

    public static ContentBlock text(String text) {
        ContentBlock block = new ContentBlock();
        block.type = "text";
        block.text = text;
        return block;
    }

    public static ContentBlock image(String attachmentPath) {
        ContentBlock block = new ContentBlock();
        block.type = "image";
        block.source = new LinkedHashMap<>();
        block.source.put("type", "attachment");
        block.source.put("path", attachmentPath);
        return block;
    }

    public static ContentBlock file(String attachmentPath) {
        ContentBlock block = new ContentBlock();
        block.type = "file";
        block.source = new LinkedHashMap<>();
        block.source.put("type", "attachment");
        block.source.put("path", attachmentPath);
        return block;
    }

    public static ContentBlock toolUse(String id, String name, Map<String, Object> input) {
        ContentBlock block = new ContentBlock();
        block.type = "tool_use";
        block.id = id;
        block.name = name;
        block.input = input != null ? new LinkedHashMap<>(input) : new LinkedHashMap<>();
        return block;
    }

    public static ContentBlock toolResult(String toolUseId, Object content) {
        ContentBlock block = new ContentBlock();
        block.type = "tool_result";
        block.toolUseId = toolUseId;
        block.content = content;
        return block;
    }

    public String getType() { return type; }
    public String getText() { return text; }
    public Map<String, String> getSource() { return source; }
    public String getId() { return id; }
    public String getName() { return name; }
    public Map<String, Object> getInput() { return input; }
    public String getToolUseId() { return toolUseId; }
    public Object getContent() { return content; }

    // Package-private setters for JSON parsing
    void setType(String type) { this.type = type; }
    void setText(String text) { this.text = text; }
    void setSource(Map<String, String> source) { this.source = source; }
    void setId(String id) { this.id = id; }
    void setName(String name) { this.name = name; }
    void setInput(Map<String, Object> input) { this.input = input; }
    void setToolUseId(String toolUseId) { this.toolUseId = toolUseId; }
    void setContent(Object content) { this.content = content; }
}
