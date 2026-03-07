package ca.weblite.opa;

import java.util.*;

/**
 * Represents a parsed OPA archive. Returned by {@link OpaReader}.
 */
public class OpaArchive {

    private final OpaManifest manifest;
    private final String prompt;
    private final SessionHistory sessionHistory;
    private final DataIndex dataIndex;
    private final Map<String, byte[]> entries;
    private final boolean signed;

    OpaArchive(OpaManifest manifest, String prompt, SessionHistory sessionHistory,
               DataIndex dataIndex, Map<String, byte[]> entries) {
        this(manifest, prompt, sessionHistory, dataIndex, entries, false);
    }

    OpaArchive(OpaManifest manifest, String prompt, SessionHistory sessionHistory,
               DataIndex dataIndex, Map<String, byte[]> entries, boolean signed) {
        this.manifest = manifest;
        this.prompt = prompt;
        this.sessionHistory = sessionHistory;
        this.dataIndex = dataIndex;
        this.entries = entries;
        this.signed = signed;
    }

    public OpaManifest getManifest() {
        return manifest;
    }

    public String getPrompt() {
        return prompt;
    }

    public SessionHistory getSessionHistory() {
        return sessionHistory;
    }

    public DataIndex getDataIndex() {
        return dataIndex;
    }

    /**
     * Returns true if the archive contains digital signature files.
     */
    public boolean isSigned() {
        return signed;
    }

    /**
     * Returns the raw bytes of a specific entry in the archive.
     * @param path the archive-relative path (e.g., "data/report.csv")
     * @return the entry bytes, or null if not found
     */
    public byte[] getEntry(String path) {
        return entries.get(path);
    }

    /**
     * Returns all entry paths in the archive.
     */
    public Set<String> getEntryPaths() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    /**
     * Returns entry paths under the data root directory.
     */
    public List<String> getDataEntryPaths() {
        String dataRoot = manifest.getDataRoot();
        List<String> result = new ArrayList<>();
        for (String path : entries.keySet()) {
            if (path.startsWith(dataRoot) && !path.equals(dataRoot + "INDEX.json")) {
                result.add(path);
            }
        }
        return result;
    }

    /**
     * Returns entry paths under the session attachments directory.
     */
    public List<String> getAttachmentPaths() {
        List<String> result = new ArrayList<>();
        for (String path : entries.keySet()) {
            if (path.startsWith("session/attachments/")) {
                result.add(path);
            }
        }
        return result;
    }
}
