package com.openprompt.opa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the data/INDEX.json file listing data assets.
 */
public class DataIndex {

    private final List<DataAsset> assets = new ArrayList<>();

    public DataIndex() {}

    public List<DataAsset> getAssets() {
        return Collections.unmodifiableList(assets);
    }

    public DataIndex addAsset(DataAsset asset) {
        assets.add(asset);
        return this;
    }

    public DataIndex addAsset(String path, String description, String contentType) {
        assets.add(new DataAsset(path, description, contentType));
        return this;
    }
}
