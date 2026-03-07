package com.openprompt.opa;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates OPA archive files.
 *
 * <p>Example usage:</p>
 * <pre>
 * OpaWriter writer = new OpaWriter();
 * writer.setManifest(manifest);
 * writer.setPrompt("Analyse the data files.");
 * writer.setSessionHistory(session);
 * writer.addDataFile("data/report.csv", csvBytes);
 * writer.writeTo(new File("task.opa"));
 * </pre>
 */
public class OpaWriter {

    private OpaManifest manifest;
    private String prompt;
    private SessionHistory sessionHistory;
    private DataIndex dataIndex;
    private final java.util.List<DataEntry> dataEntries = new java.util.ArrayList<>();
    private final java.util.List<DataEntry> attachmentEntries = new java.util.ArrayList<>();

    public OpaWriter() {
        this.manifest = new OpaManifest();
    }

    public OpaManifest getManifest() {
        return manifest;
    }

    public OpaWriter setManifest(OpaManifest manifest) {
        this.manifest = manifest;
        return this;
    }

    public OpaWriter setPrompt(String prompt) {
        this.prompt = prompt;
        return this;
    }

    public OpaWriter setSessionHistory(SessionHistory sessionHistory) {
        this.sessionHistory = sessionHistory;
        return this;
    }

    public OpaWriter setDataIndex(DataIndex dataIndex) {
        this.dataIndex = dataIndex;
        return this;
    }

    /**
     * Adds a data file to the archive.
     * @param archivePath the path within the archive (e.g., "data/report.csv")
     * @param content the file content
     */
    public OpaWriter addDataFile(String archivePath, byte[] content) {
        dataEntries.add(new DataEntry(archivePath, content));
        return this;
    }

    /**
     * Adds a data file from a string.
     */
    public OpaWriter addDataFile(String archivePath, String content) {
        return addDataFile(archivePath, content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Adds a session attachment file.
     * @param archivePath the path within the archive (e.g., "session/attachments/image.png")
     * @param content the file content
     */
    public OpaWriter addAttachment(String archivePath, byte[] content) {
        attachmentEntries.add(new DataEntry(archivePath, content));
        return this;
    }

    /**
     * Adds all files from a directory as data assets.
     * @param dir the directory to add
     * @param archivePrefix the prefix in the archive (e.g., "data/")
     */
    public OpaWriter addDataDirectory(File dir, String archivePrefix) throws IOException {
        Path basePath = dir.toPath();
        Files.walkFileTree(basePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String relativePath = basePath.relativize(file).toString().replace('\\', '/');
                String archivePath = archivePrefix + relativePath;
                dataEntries.add(new DataEntry(archivePath, Files.readAllBytes(file)));
                return FileVisitResult.CONTINUE;
            }
        });
        return this;
    }

    /**
     * Writes the OPA archive to a file.
     */
    public void writeTo(File file) throws IOException, OpaException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            writeTo(fos);
        }
    }

    /**
     * Writes the OPA archive to an output stream.
     */
    public void writeTo(OutputStream out) throws IOException, OpaException {
        validate();

        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            // Write manifest
            zos.putNextEntry(new ZipEntry(OpaManifest.MANIFEST_PATH));
            ByteArrayOutputStream manifestBuf = new ByteArrayOutputStream();
            manifest.writeTo(manifestBuf);
            zos.write(manifestBuf.toByteArray());
            zos.closeEntry();

            // Write prompt file
            String promptFile = manifest.getPromptFile();
            zos.putNextEntry(new ZipEntry(promptFile));
            zos.write(prompt.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // Write session history
            if (sessionHistory != null) {
                String sessionFile = manifest.getSessionFile();
                zos.putNextEntry(new ZipEntry(sessionFile));
                ByteArrayOutputStream sessionBuf = new ByteArrayOutputStream();
                SessionHistoryJson.write(sessionHistory, sessionBuf);
                zos.write(sessionBuf.toByteArray());
                zos.closeEntry();
            }

            // Write attachments
            for (DataEntry entry : attachmentEntries) {
                validatePath(entry.path);
                zos.putNextEntry(new ZipEntry(entry.path));
                zos.write(entry.content);
                zos.closeEntry();
            }

            // Write data index
            if (dataIndex != null) {
                zos.putNextEntry(new ZipEntry(manifest.getDataRoot() + "INDEX.json"));
                ByteArrayOutputStream indexBuf = new ByteArrayOutputStream();
                DataIndexJson.write(dataIndex, indexBuf);
                zos.write(indexBuf.toByteArray());
                zos.closeEntry();
            }

            // Write data files
            for (DataEntry entry : dataEntries) {
                validatePath(entry.path);
                zos.putNextEntry(new ZipEntry(entry.path));
                zos.write(entry.content);
                zos.closeEntry();
            }
        }
    }

    private void validate() throws OpaException {
        if (manifest == null) {
            throw new OpaException("Manifest is required");
        }
        if (prompt == null || prompt.isEmpty()) {
            throw new OpaException("Prompt content is required");
        }
    }

    static void validatePath(String path) throws OpaException {
        if (path.contains("..") || path.startsWith("/")) {
            throw new OpaException("Invalid archive path (path traversal detected): " + path);
        }
    }

    private static class DataEntry {
        final String path;
        final byte[] content;

        DataEntry(String path, byte[] content) {
            this.path = path;
            this.content = content;
        }
    }
}
