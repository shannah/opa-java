package ca.weblite.opa;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents the META-INF/MANIFEST.MF file in an OPA archive.
 * Follows the JAR manifest format specification.
 */
public class OpaManifest {

    public static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";
    public static final String DEFAULT_PROMPT_FILE = "prompt.md";
    public static final String DEFAULT_SESSION_FILE = "session/history.json";
    public static final String DEFAULT_DATA_ROOT = "data/";
    public static final String OPA_VERSION = "0.1";
    public static final String MANIFEST_VERSION = "1.0";

    private final Map<String, String> attributes = new LinkedHashMap<>();

    public OpaManifest() {
        attributes.put("Manifest-Version", MANIFEST_VERSION);
        attributes.put("OPA-Version", OPA_VERSION);
    }

    public String getManifestVersion() {
        return attributes.get("Manifest-Version");
    }

    public String getOpaVersion() {
        return attributes.get("OPA-Version");
    }

    public String getPromptFile() {
        return attributes.getOrDefault("Prompt-File", DEFAULT_PROMPT_FILE);
    }

    public OpaManifest setPromptFile(String promptFile) {
        attributes.put("Prompt-File", promptFile);
        return this;
    }

    public String getTitle() {
        return attributes.get("Title");
    }

    public OpaManifest setTitle(String title) {
        attributes.put("Title", title);
        return this;
    }

    public String getDescription() {
        return attributes.get("Description");
    }

    public OpaManifest setDescription(String description) {
        attributes.put("Description", description);
        return this;
    }

    public String getAgentHint() {
        return attributes.get("Agent-Hint");
    }

    public OpaManifest setAgentHint(String agentHint) {
        attributes.put("Agent-Hint", agentHint);
        return this;
    }

    public String getCreatedBy() {
        return attributes.get("Created-By");
    }

    public OpaManifest setCreatedBy(String createdBy) {
        attributes.put("Created-By", createdBy);
        return this;
    }

    public String getCreatedAt() {
        return attributes.get("Created-At");
    }

    public OpaManifest setCreatedAt(String createdAt) {
        attributes.put("Created-At", createdAt);
        return this;
    }

    public String getSessionFile() {
        return attributes.getOrDefault("Session-File", DEFAULT_SESSION_FILE);
    }

    public OpaManifest setSessionFile(String sessionFile) {
        attributes.put("Session-File", sessionFile);
        return this;
    }

    public String getDataRoot() {
        return attributes.getOrDefault("Data-Root", DEFAULT_DATA_ROOT);
    }

    public OpaManifest setDataRoot(String dataRoot) {
        attributes.put("Data-Root", dataRoot);
        return this;
    }

    public ExecutionMode getExecutionMode() {
        String value = attributes.get("Execution-Mode");
        return value != null ? ExecutionMode.fromValue(value) : ExecutionMode.INTERACTIVE;
    }

    public OpaManifest setExecutionMode(ExecutionMode mode) {
        attributes.put("Execution-Mode", mode.getValue());
        return this;
    }

    public String getSchemaExtensions() {
        return attributes.get("Schema-Extensions");
    }

    public OpaManifest setSchemaExtensions(String schemaExtensions) {
        attributes.put("Schema-Extensions", schemaExtensions);
        return this;
    }

    public String getAttribute(String name) {
        return attributes.get(name);
    }

    public OpaManifest setAttribute(String name, String value) {
        attributes.put(name, value);
        return this;
    }

    public Map<String, String> getAttributes() {
        return new LinkedHashMap<>(attributes);
    }

    /**
     * Writes this manifest in JAR manifest format.
     * Lines exceeding 72 bytes are continued on the next line with a leading space.
     */
    public void writeTo(OutputStream out) throws IOException {
        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            String line = entry.getKey() + ": " + entry.getValue();
            writeManifestLine(writer, line);
        }
        writer.write("\r\n");
        writer.flush();
    }

    private void writeManifestLine(Writer writer, String line) throws IOException {
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 72) {
            writer.write(line);
            writer.write("\r\n");
            return;
        }
        // First line: up to 72 bytes
        int offset = 0;
        int limit = 72;
        String first = cutAtByteLimit(line, offset, limit);
        writer.write(first);
        writer.write("\r\n");
        offset = first.length();

        // Continuation lines: space + up to 71 bytes of content
        while (offset < line.length()) {
            String continuation = cutAtByteLimit(line, offset, 71);
            writer.write(" ");
            writer.write(continuation);
            writer.write("\r\n");
            offset += continuation.length();
        }
    }

    /**
     * Returns the longest substring starting at charOffset whose UTF-8 byte length <= maxBytes.
     */
    private String cutAtByteLimit(String s, int charOffset, int maxBytes) {
        int end = Math.min(s.length(), charOffset + maxBytes);
        while (end > charOffset) {
            String sub = s.substring(charOffset, end);
            if (sub.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
                return sub;
            }
            end--;
        }
        return s.substring(charOffset, charOffset + 1);
    }

    /**
     * Parses a manifest from an input stream.
     */
    public static OpaManifest parse(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        OpaManifest manifest = new OpaManifest();
        manifest.attributes.clear();

        StringBuilder currentLine = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                // End of main section
                break;
            }
            if (line.startsWith(" ")) {
                // Continuation line
                currentLine.append(line.substring(1));
            } else {
                // Process previous accumulated line
                if (currentLine.length() > 0) {
                    parseAttribute(manifest, currentLine.toString());
                }
                currentLine = new StringBuilder(line);
            }
        }
        // Process last accumulated line
        if (currentLine.length() > 0) {
            parseAttribute(manifest, currentLine.toString());
        }

        return manifest;
    }

    private static void parseAttribute(OpaManifest manifest, String line) {
        int colonIndex = line.indexOf(':');
        if (colonIndex > 0) {
            String name = line.substring(0, colonIndex).trim();
            String value = line.substring(colonIndex + 1).trim();
            manifest.attributes.put(name, value);
        }
    }
}
