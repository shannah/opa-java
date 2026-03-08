package ca.weblite.opa;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.util.*;

import static org.junit.Assert.*;

public class OpaWriterReaderTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testMinimalArchive() throws Exception {
        File opaFile = tempFolder.newFile("minimal.opa");

        OpaWriter writer = new OpaWriter();
        writer.setPrompt("Hello, please help me.");
        writer.writeTo(opaFile);

        OpaArchive archive = OpaReader.read(opaFile);
        assertEquals("Hello, please help me.", archive.getPrompt());
        assertEquals("1.0", archive.getManifest().getManifestVersion());
        assertEquals("0.1", archive.getManifest().getOpaVersion());
        assertNull(archive.getSessionHistory());
        assertNull(archive.getDataIndex());
    }

    @Test
    public void testFullArchive() throws Exception {
        File opaFile = tempFolder.newFile("full.opa");

        OpaManifest manifest = new OpaManifest();
        manifest.setTitle("Test Task")
                .setDescription("A test task with all features")
                .setExecutionMode(ExecutionMode.BATCH)
                .setCreatedBy("opa-test")
                .setCreatedAt("2026-03-04T12:00:00Z")
                .setAgentHint("claude-sonnet");

        SessionHistory session = new SessionHistory("f47ac10b-58cc-4372-a567-0e02b2c3d479");
        session.setCreatedAt("2026-03-01T10:00:00Z");
        session.setUpdatedAt("2026-03-03T17:45:00Z");
        session.addMessage(new Message(MessageRole.USER, "Can you start by reviewing the data?")
                .setId("1").setTimestamp("2026-03-01T10:00:00Z"));
        session.addMessage(new Message(MessageRole.ASSISTANT, "Sure! I can see the CSV has three columns...")
                .setId("2").setTimestamp("2026-03-01T10:00:05Z"));

        DataIndex dataIndex = new DataIndex();
        dataIndex.addAsset("data/report.csv", "Q1 sales data", "text/csv");

        OpaWriter writer = new OpaWriter();
        writer.setManifest(manifest);
        writer.setPrompt("Analyse the sales data in `data/report.csv`.");
        writer.setSessionHistory(session);
        writer.setDataIndex(dataIndex);
        writer.addDataFile("data/report.csv", "region,sales\nnorth,100\nsouth,200\n");
        writer.writeTo(opaFile);

        // Read it back
        OpaArchive archive = OpaReader.read(opaFile);
        assertEquals("Test Task", archive.getManifest().getTitle());
        assertEquals("A test task with all features", archive.getManifest().getDescription());
        assertEquals(ExecutionMode.BATCH, archive.getManifest().getExecutionMode());
        assertEquals("Analyse the sales data in `data/report.csv`.", archive.getPrompt());

        // Session
        assertNotNull(archive.getSessionHistory());
        assertEquals("f47ac10b-58cc-4372-a567-0e02b2c3d479", archive.getSessionHistory().getSessionId());
        assertEquals(2, archive.getSessionHistory().getMessages().size());
        assertEquals(MessageRole.USER, archive.getSessionHistory().getMessages().get(0).getRole());
        assertEquals("Can you start by reviewing the data?", archive.getSessionHistory().getMessages().get(0).getTextContent());

        // Data index
        assertNotNull(archive.getDataIndex());
        assertEquals(1, archive.getDataIndex().getAssets().size());
        assertEquals("data/report.csv", archive.getDataIndex().getAssets().get(0).getPath());

        // Data file
        assertNotNull(archive.getEntry("data/report.csv"));
        assertEquals("region,sales\nnorth,100\nsouth,200\n",
                new String(archive.getEntry("data/report.csv"), "UTF-8"));

        // Data entry paths
        List<String> dataPaths = archive.getDataEntryPaths();
        assertTrue(dataPaths.contains("data/report.csv"));
    }

    @Test
    public void testMultiContentMessages() throws Exception {
        File opaFile = tempFolder.newFile("multi.opa");

        SessionHistory session = new SessionHistory("test-session-id");
        List<ContentBlock> blocks = Arrays.asList(
                ContentBlock.text("Here is an image:"),
                ContentBlock.image("session/attachments/diagram.png")
        );
        session.addMessage(new Message(MessageRole.USER, blocks).setId("1"));

        OpaWriter writer = new OpaWriter();
        writer.setPrompt("Describe the attached image.");
        writer.setSessionHistory(session);
        writer.addAttachment("session/attachments/diagram.png", new byte[]{1, 2, 3});
        writer.writeTo(opaFile);

        OpaArchive archive = OpaReader.read(opaFile);
        assertNotNull(archive.getSessionHistory());
        Message msg = archive.getSessionHistory().getMessages().get(0);
        assertTrue(msg.isMultiContent());
        assertEquals(2, msg.getContentBlocks().size());
        assertEquals("text", msg.getContentBlocks().get(0).getType());
        assertEquals("Here is an image:", msg.getContentBlocks().get(0).getText());
        assertEquals("image", msg.getContentBlocks().get(1).getType());
        assertEquals("session/attachments/diagram.png", msg.getContentBlocks().get(1).getSource().get("path"));

        // Verify attachment
        List<String> attachments = archive.getAttachmentPaths();
        assertEquals(1, attachments.size());
        assertEquals("session/attachments/diagram.png", attachments.get(0));
    }

    @Test
    public void testToolUseMessages() throws Exception {
        File opaFile = tempFolder.newFile("tools.opa");

        SessionHistory session = new SessionHistory("tool-session");
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", "SELECT * FROM sales");

        List<ContentBlock> assistantBlocks = Arrays.asList(
                ContentBlock.text("Let me query the database."),
                ContentBlock.toolUse("call-1", "sql_query", input)
        );
        session.addMessage(new Message(MessageRole.ASSISTANT, assistantBlocks).setId("1"));

        List<ContentBlock> toolBlocks = Collections.singletonList(
                ContentBlock.toolResult("call-1", "3 rows returned")
        );
        session.addMessage(new Message(MessageRole.TOOL, toolBlocks).setId("2"));

        OpaWriter writer = new OpaWriter();
        writer.setPrompt("Continue the analysis.");
        writer.setSessionHistory(session);
        writer.writeTo(opaFile);

        OpaArchive archive = OpaReader.read(opaFile);
        List<Message> messages = archive.getSessionHistory().getMessages();
        assertEquals(2, messages.size());

        // Assistant message with tool_use
        ContentBlock toolUse = messages.get(0).getContentBlocks().get(1);
        assertEquals("tool_use", toolUse.getType());
        assertEquals("call-1", toolUse.getId());
        assertEquals("sql_query", toolUse.getName());
        assertEquals("SELECT * FROM sales", toolUse.getInput().get("query"));

        // Tool result message
        ContentBlock toolResult = messages.get(1).getContentBlocks().get(0);
        assertEquals("tool_result", toolResult.getType());
        assertEquals("call-1", toolResult.getToolUseId());
        assertEquals("3 rows returned", toolResult.getContent());
    }

    @Test(expected = OpaException.class)
    public void testMissingPromptThrows() throws Exception {
        File opaFile = tempFolder.newFile("invalid.opa");
        OpaWriter writer = new OpaWriter();
        writer.writeTo(opaFile);
    }

    @Test(expected = OpaException.class)
    public void testPathTraversalRejected() throws Exception {
        File opaFile = tempFolder.newFile("traversal.opa");
        OpaWriter writer = new OpaWriter();
        writer.setPrompt("test");
        writer.addDataFile("../etc/passwd", "bad");
        writer.writeTo(opaFile);
    }

    @Test
    public void testReadFromInputStream() throws Exception {
        File opaFile = tempFolder.newFile("stream.opa");

        OpaWriter writer = new OpaWriter();
        writer.getManifest().setTitle("Stream Test");
        writer.setPrompt("Test prompt for stream reading.");
        writer.writeTo(opaFile);

        // Read from InputStream instead of File
        try (FileInputStream fis = new FileInputStream(opaFile)) {
            OpaArchive archive = OpaReader.read(fis);
            assertEquals("Stream Test", archive.getManifest().getTitle());
            assertEquals("Test prompt for stream reading.", archive.getPrompt());
        }
    }

    @Test
    public void testCustomPromptFile() throws Exception {
        File opaFile = tempFolder.newFile("custom.opa");

        OpaManifest manifest = new OpaManifest();
        manifest.setPromptFile("instructions.md");

        OpaWriter writer = new OpaWriter();
        writer.setManifest(manifest);
        writer.setPrompt("Custom prompt file path test.");
        writer.writeTo(opaFile);

        OpaArchive archive = OpaReader.read(opaFile);
        assertEquals("instructions.md", archive.getManifest().getPromptFile());
        assertEquals("Custom prompt file path test.", archive.getPrompt());
    }

    @Test
    public void testDataDirectory() throws Exception {
        File dataDir = tempFolder.newFolder("testdata");
        File subDir = new File(dataDir, "sub");
        subDir.mkdirs();

        // Create some test files
        writeFile(new File(dataDir, "file1.txt"), "content1");
        writeFile(new File(subDir, "file2.txt"), "content2");

        File opaFile = tempFolder.newFile("withdir.opa");

        OpaWriter writer = new OpaWriter();
        writer.setPrompt("Analyse the data.");
        writer.addDataDirectory(dataDir, "data/");
        writer.writeTo(opaFile);

        OpaArchive archive = OpaReader.read(opaFile);
        assertNotNull(archive.getEntry("data/file1.txt"));
        assertNotNull(archive.getEntry("data/sub/file2.txt"));
        assertEquals("content1", new String(archive.getEntry("data/file1.txt"), "UTF-8"));
        assertEquals("content2", new String(archive.getEntry("data/sub/file2.txt"), "UTF-8"));
    }

    private void writeFile(File file, String content) throws IOException {
        try (FileWriter fw = new FileWriter(file)) {
            fw.write(content);
        }
    }
}
