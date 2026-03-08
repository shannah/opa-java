package ca.weblite.opa;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Handles serialization and deserialization of DataIndex to/from JSON.
 */
class DataIndexJson {

    @SuppressWarnings("unchecked")
    static DataIndex parse(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        String json = baos.toString("UTF-8");
        Map<String, Object> root = (Map<String, Object>) Json.parse(json);

        DataIndex index = new DataIndex();
        List<Object> assets = (List<Object>) root.get("assets");
        if (assets != null) {
            for (Object assetObj : assets) {
                Map<String, Object> assetMap = (Map<String, Object>) assetObj;
                DataAsset asset = new DataAsset();
                asset.setPath((String) assetMap.get("path"));
                asset.setDescription((String) assetMap.get("description"));
                asset.setContentType((String) assetMap.get("content_type"));
                index.addAsset(asset);
            }
        }
        return index;
    }

    static void write(DataIndex index, OutputStream out) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        List<Object> assets = new ArrayList<>();
        for (DataAsset asset : index.getAssets()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("path", asset.getPath());
            if (asset.getDescription() != null) {
                map.put("description", asset.getDescription());
            }
            if (asset.getContentType() != null) {
                map.put("content_type", asset.getContentType());
            }
            assets.add(map);
        }
        root.put("assets", assets);
        out.write(Json.write(root).getBytes(StandardCharsets.UTF_8));
    }
}
