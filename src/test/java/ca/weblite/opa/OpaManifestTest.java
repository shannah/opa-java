package ca.weblite.opa;

import org.junit.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class OpaManifestTest {

    @Test
    public void testDefaultValues() {
        OpaManifest manifest = new OpaManifest();
        assertEquals("1.0", manifest.getManifestVersion());
        assertEquals("0.1", manifest.getOpaVersion());
        assertEquals("prompt.md", manifest.getPromptFile());
        assertEquals("session/history.json", manifest.getSessionFile());
        assertEquals("data/", manifest.getDataRoot());
        assertEquals(ExecutionMode.INTERACTIVE, manifest.getExecutionMode());
    }

    @Test
    public void testSettersAndGetters() {
        OpaManifest manifest = new OpaManifest();
        manifest.setTitle("Test Task")
                .setDescription("A test description")
                .setAgentHint("claude-sonnet")
                .setCreatedBy("opa-test 1.0")
                .setCreatedAt("2026-03-04T12:00:00Z")
                .setExecutionMode(ExecutionMode.BATCH)
                .setPromptFile("custom-prompt.md")
                .setSessionFile("custom/session.json")
                .setDataRoot("assets/");

        assertEquals("Test Task", manifest.getTitle());
        assertEquals("A test description", manifest.getDescription());
        assertEquals("claude-sonnet", manifest.getAgentHint());
        assertEquals("opa-test 1.0", manifest.getCreatedBy());
        assertEquals("2026-03-04T12:00:00Z", manifest.getCreatedAt());
        assertEquals(ExecutionMode.BATCH, manifest.getExecutionMode());
        assertEquals("custom-prompt.md", manifest.getPromptFile());
        assertEquals("custom/session.json", manifest.getSessionFile());
        assertEquals("assets/", manifest.getDataRoot());
    }

    @Test
    public void testWriteAndParse() throws IOException {
        OpaManifest original = new OpaManifest();
        original.setTitle("Summarise Q1 Sales")
                .setDescription("Summarise the attached regional CSV files")
                .setCreatedBy("opa-cli 1.0.0")
                .setCreatedAt("2026-03-04T09:15:00Z")
                .setAgentHint("claude-sonnet")
                .setExecutionMode(ExecutionMode.BATCH);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        original.writeTo(out);

        OpaManifest parsed = OpaManifest.parse(new ByteArrayInputStream(out.toByteArray()));

        assertEquals(original.getManifestVersion(), parsed.getManifestVersion());
        assertEquals(original.getOpaVersion(), parsed.getOpaVersion());
        assertEquals(original.getTitle(), parsed.getTitle());
        assertEquals(original.getDescription(), parsed.getDescription());
        assertEquals(original.getCreatedBy(), parsed.getCreatedBy());
        assertEquals(original.getCreatedAt(), parsed.getCreatedAt());
        assertEquals(original.getAgentHint(), parsed.getAgentHint());
        assertEquals(original.getExecutionMode(), parsed.getExecutionMode());
    }

    @Test
    public void testLineContinuation() throws IOException {
        OpaManifest manifest = new OpaManifest();
        // Set a very long description that will exceed 72 bytes
        String longDesc = "This is a very long description that should definitely exceed the 72 byte line limit in the manifest format";
        manifest.setDescription(longDesc);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        manifest.writeTo(out);
        String written = out.toString("UTF-8");

        // Verify continuation lines exist (lines starting with space)
        assertTrue("Should contain continuation lines", written.contains("\r\n "));

        // Parse it back and verify
        OpaManifest parsed = OpaManifest.parse(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(longDesc, parsed.getDescription());
    }

    @Test
    public void testCustomAttributes() {
        OpaManifest manifest = new OpaManifest();
        manifest.setAttribute("X-Custom", "custom-value");
        assertEquals("custom-value", manifest.getAttribute("X-Custom"));
    }

    @Test
    public void testParseSpecExample() throws IOException {
        String input = "Manifest-Version: 1.0\r\n"
                + "OPA-Version: 0.1\r\n"
                + "Title: Summarise Q1 Sales Reports\r\n"
                + "Description: Summarise the attached regional CSV files and draft an execut\r\n"
                + " ive brief.\r\n"
                + "Created-By: opa-cli 1.0.0\r\n"
                + "Created-At: 2026-03-04T09:15:00Z\r\n"
                + "Agent-Hint: claude-sonnet\r\n"
                + "Execution-Mode: batch\r\n"
                + "Prompt-File: prompt.md\r\n"
                + "Session-File: session/history.json\r\n"
                + "Data-Root: data/\r\n"
                + "\r\n";

        OpaManifest manifest = OpaManifest.parse(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));

        assertEquals("1.0", manifest.getManifestVersion());
        assertEquals("0.1", manifest.getOpaVersion());
        assertEquals("Summarise Q1 Sales Reports", manifest.getTitle());
        assertEquals("Summarise the attached regional CSV files and draft an executive brief.", manifest.getDescription());
        assertEquals("opa-cli 1.0.0", manifest.getCreatedBy());
        assertEquals(ExecutionMode.BATCH, manifest.getExecutionMode());
    }
}
