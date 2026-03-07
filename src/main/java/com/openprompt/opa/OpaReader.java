package com.openprompt.opa;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Reads OPA archive files.
 *
 * <p>Example usage:</p>
 * <pre>
 * OpaArchive archive = OpaReader.read(new File("task.opa"));
 * String prompt = archive.getPrompt();
 * OpaManifest manifest = archive.getManifest();
 * SessionHistory session = archive.getSessionHistory();
 * </pre>
 */
public class OpaReader {

    /**
     * Reads an OPA archive from a file.
     */
    public static OpaArchive read(File file) throws IOException, OpaException {
        try (ZipFile zipFile = new ZipFile(file)) {
            return readFromZipFile(zipFile);
        }
    }

    /**
     * Reads an OPA archive from an input stream.
     */
    public static OpaArchive read(InputStream in) throws IOException, OpaException {
        return readFromStream(in);
    }

    private static OpaArchive readFromZipFile(ZipFile zipFile) throws IOException, OpaException {
        // Read and validate manifest
        ZipEntry manifestEntry = zipFile.getEntry(OpaManifest.MANIFEST_PATH);
        if (manifestEntry == null) {
            throw new OpaException("Missing required entry: " + OpaManifest.MANIFEST_PATH);
        }
        OpaManifest manifest;
        try (InputStream is = zipFile.getInputStream(manifestEntry)) {
            manifest = OpaManifest.parse(is);
        }
        validateManifest(manifest);

        // Validate all paths
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            OpaWriter.validatePath(entry.getName());
        }

        // Read prompt
        String promptFile = manifest.getPromptFile();
        ZipEntry promptEntry = zipFile.getEntry(promptFile);
        if (promptEntry == null) {
            throw new OpaException("Missing required prompt file: " + promptFile);
        }
        String prompt;
        try (InputStream is = zipFile.getInputStream(promptEntry)) {
            prompt = readString(is);
        }

        // Read session history if present
        SessionHistory sessionHistory = null;
        String sessionFile = manifest.getSessionFile();
        ZipEntry sessionEntry = zipFile.getEntry(sessionFile);
        if (sessionEntry != null) {
            try (InputStream is = zipFile.getInputStream(sessionEntry)) {
                sessionHistory = SessionHistoryJson.parse(is);
            }
        }

        // Read data index if present
        DataIndex dataIndex = null;
        String dataIndexPath = manifest.getDataRoot() + "INDEX.json";
        ZipEntry indexEntry = zipFile.getEntry(dataIndexPath);
        if (indexEntry != null) {
            try (InputStream is = zipFile.getInputStream(indexEntry)) {
                dataIndex = DataIndexJson.parse(is);
            }
        }

        // Collect all entries
        Map<String, byte[]> allEntries = new LinkedHashMap<>();
        entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory()) {
                try (InputStream is = zipFile.getInputStream(entry)) {
                    allEntries.put(entry.getName(), readBytes(is));
                }
            }
        }

        return new OpaArchive(manifest, prompt, sessionHistory, dataIndex, allEntries);
    }

    private static OpaArchive readFromStream(InputStream in) throws IOException, OpaException {
        // Read all entries into memory
        Map<String, byte[]> allEntries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                OpaWriter.validatePath(entry.getName());
                if (!entry.isDirectory()) {
                    allEntries.put(entry.getName(), readBytes(zis));
                }
                zis.closeEntry();
            }
        }

        // Parse manifest
        byte[] manifestBytes = allEntries.get(OpaManifest.MANIFEST_PATH);
        if (manifestBytes == null) {
            throw new OpaException("Missing required entry: " + OpaManifest.MANIFEST_PATH);
        }
        OpaManifest manifest = OpaManifest.parse(new ByteArrayInputStream(manifestBytes));
        validateManifest(manifest);

        // Read prompt
        String promptFile = manifest.getPromptFile();
        byte[] promptBytes = allEntries.get(promptFile);
        if (promptBytes == null) {
            throw new OpaException("Missing required prompt file: " + promptFile);
        }
        String prompt = new String(promptBytes, StandardCharsets.UTF_8);

        // Read session history
        SessionHistory sessionHistory = null;
        String sessionFile = manifest.getSessionFile();
        byte[] sessionBytes = allEntries.get(sessionFile);
        if (sessionBytes != null) {
            sessionHistory = SessionHistoryJson.parse(new ByteArrayInputStream(sessionBytes));
        }

        // Read data index
        DataIndex dataIndex = null;
        String dataIndexPath = manifest.getDataRoot() + "INDEX.json";
        byte[] indexBytes = allEntries.get(dataIndexPath);
        if (indexBytes != null) {
            dataIndex = DataIndexJson.parse(new ByteArrayInputStream(indexBytes));
        }

        return new OpaArchive(manifest, prompt, sessionHistory, dataIndex, allEntries);
    }

    private static void validateManifest(OpaManifest manifest) throws OpaException {
        if (manifest.getManifestVersion() == null) {
            throw new OpaException("Missing required manifest field: Manifest-Version");
        }
        if (manifest.getOpaVersion() == null) {
            throw new OpaException("Missing required manifest field: OPA-Version");
        }
    }

    private static String readString(InputStream in) throws IOException {
        return new String(readBytes(in), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }
}
