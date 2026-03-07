package com.openprompt.opa;

/**
 * Represents an entry in the data/INDEX.json file.
 */
public class DataAsset {

    private String path;
    private String description;
    private String contentType;

    public DataAsset() {}

    public DataAsset(String path, String description, String contentType) {
        this.path = path;
        this.description = description;
        this.contentType = contentType;
    }

    public String getPath() { return path; }
    public DataAsset setPath(String path) { this.path = path; return this; }

    public String getDescription() { return description; }
    public DataAsset setDescription(String description) { this.description = description; return this; }

    public String getContentType() { return contentType; }
    public DataAsset setContentType(String contentType) { this.contentType = contentType; return this; }
}
