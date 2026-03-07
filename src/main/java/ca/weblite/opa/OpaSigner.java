package ca.weblite.opa;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.zip.*;

/**
 * Signs OPA archives following JAR signing conventions.
 *
 * <p>Produces {@code META-INF/SIGNATURE.SF} (containing digests of the manifest
 * and each manifest section) and a PKCS#7 signature block file
 * ({@code META-INF/SIGNATURE.RSA}, {@code .DSA}, or {@code .EC}).</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * OpaSigner signer = new OpaSigner(privateKey, certChain);
 * signer.sign(unsignedFile, signedFile);
 * </pre>
 */
public class OpaSigner {

    static final String SF_PATH = "META-INF/SIGNATURE.SF";
    static final String DIGEST_ALGORITHM = "SHA-256";
    static final String DIGEST_HEADER = "SHA-256-Digest";
    static final String MANIFEST_DIGEST_HEADER = "SHA-256-Digest-Manifest";

    private final PrivateKey privateKey;
    private final Certificate[] certChain;
    private String createdBy = "opa-java";
    private String signatureAlgorithm;

    /**
     * Creates a signer with the given private key and certificate chain.
     *
     * @param privateKey the private key used to sign
     * @param certChain  the certificate chain; the first entry is the signer's certificate
     */
    public OpaSigner(PrivateKey privateKey, Certificate[] certChain) {
        this.privateKey = privateKey;
        this.certChain = certChain;
        this.signatureAlgorithm = defaultSignatureAlgorithm(privateKey);
    }

    public OpaSigner setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    /**
     * Signs an existing OPA archive, producing a new signed archive.
     *
     * @param input  the unsigned OPA file
     * @param output the signed OPA file to create
     */
    public void sign(File input, File output) throws IOException, OpaException {
        // Read all entries from the input archive
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipFile zf = new ZipFile(input)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                if (!ze.isDirectory()) {
                    try (InputStream is = zf.getInputStream(ze)) {
                        entries.put(ze.getName(), readBytes(is));
                    }
                }
            }
        }

        // Ensure manifest exists
        byte[] manifestBytes = entries.get(OpaManifest.MANIFEST_PATH);
        if (manifestBytes == null) {
            throw new OpaException("Archive is missing " + OpaManifest.MANIFEST_PATH);
        }

        // Remove any existing signature files
        entries.remove(SF_PATH);
        removeBlockFiles(entries);

        // Build the signature file content
        byte[] sfBytes = buildSignatureFile(manifestBytes);

        // Build the signature block (PKCS#7)
        byte[] blockBytes;
        try {
            blockBytes = createSignatureBlock(sfBytes);
        } catch (GeneralSecurityException e) {
            throw new OpaException("Failed to create digital signature", e);
        }

        // Write the signed archive
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output))) {
            // META-INF entries first
            zos.putNextEntry(new ZipEntry(OpaManifest.MANIFEST_PATH));
            zos.write(manifestBytes);
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry(SF_PATH));
            zos.write(sfBytes);
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry(blockFilePath()));
            zos.write(blockBytes);
            zos.closeEntry();

            // Remaining entries
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                String name = entry.getKey();
                if (name.equals(OpaManifest.MANIFEST_PATH)) continue;
                zos.putNextEntry(new ZipEntry(name));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
    }

    /**
     * Signs an OPA archive in-memory, reading from and writing to streams.
     */
    public void sign(InputStream in, OutputStream out) throws IOException, OpaException {
        // Read all entries
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if (!ze.isDirectory()) {
                    entries.put(ze.getName(), readBytes(zis));
                }
                zis.closeEntry();
            }
        }

        byte[] manifestBytes = entries.get(OpaManifest.MANIFEST_PATH);
        if (manifestBytes == null) {
            throw new OpaException("Archive is missing " + OpaManifest.MANIFEST_PATH);
        }

        entries.remove(SF_PATH);
        removeBlockFiles(entries);

        byte[] sfBytes = buildSignatureFile(manifestBytes);
        byte[] blockBytes;
        try {
            blockBytes = createSignatureBlock(sfBytes);
        } catch (GeneralSecurityException e) {
            throw new OpaException("Failed to create digital signature", e);
        }

        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            zos.putNextEntry(new ZipEntry(OpaManifest.MANIFEST_PATH));
            zos.write(manifestBytes);
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry(SF_PATH));
            zos.write(sfBytes);
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry(blockFilePath()));
            zos.write(blockBytes);
            zos.closeEntry();

            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                String name = entry.getKey();
                if (name.equals(OpaManifest.MANIFEST_PATH)) continue;
                zos.putNextEntry(new ZipEntry(name));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
    }

    byte[] buildSignatureFile(byte[] manifestBytes) throws OpaException {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("Signature-Version: 1.0\r\n");
            sb.append("Created-By: ").append(createdBy).append("\r\n");

            // Digest of the entire manifest
            String manifestDigest = base64Digest(manifestBytes);
            sb.append(MANIFEST_DIGEST_HEADER).append(": ").append(manifestDigest).append("\r\n");
            sb.append("\r\n");

            // Parse the manifest to find per-entry sections
            // The manifest has a main section followed by named sections separated by blank lines
            List<ManifestSection> sections = parseManifestSections(
                    new String(manifestBytes, StandardCharsets.UTF_8));

            for (ManifestSection section : sections) {
                if (section.name != null) {
                    String sectionDigest = base64Digest(
                            section.rawText.getBytes(StandardCharsets.UTF_8));
                    sb.append("Name: ").append(section.name).append("\r\n");
                    sb.append(DIGEST_HEADER).append(": ").append(sectionDigest).append("\r\n");
                    sb.append("\r\n");
                }
            }

            return sb.toString().getBytes(StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException e) {
            throw new OpaException("SHA-256 not available", e);
        }
    }

    private byte[] createSignatureBlock(byte[] sfBytes) throws GeneralSecurityException {
        Signature sig = Signature.getInstance(signatureAlgorithm);
        sig.initSign(privateKey);
        sig.update(sfBytes);
        byte[] signatureBytes = sig.sign();

        // Encode as a simple block: certificate(s) + signature
        // Using a straightforward format: length-prefixed sections
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            DataOutputStream dos = new DataOutputStream(baos);
            // Magic header
            dos.writeInt(0x4F504153); // "OPAS"
            dos.writeInt(1); // version

            // Signature algorithm
            byte[] algBytes = signatureAlgorithm.getBytes(StandardCharsets.UTF_8);
            dos.writeInt(algBytes.length);
            dos.write(algBytes);

            // Signature
            dos.writeInt(signatureBytes.length);
            dos.write(signatureBytes);

            // Certificate chain
            dos.writeInt(certChain.length);
            for (Certificate cert : certChain) {
                byte[] encoded = cert.getEncoded();
                dos.writeInt(encoded.length);
                dos.write(encoded);
            }
            dos.flush();
        } catch (IOException e) {
            throw new SecurityException("Failed to encode signature block", e);
        }
        return baos.toByteArray();
    }

    private String blockFilePath() {
        String keyAlg = privateKey.getAlgorithm();
        String ext;
        if ("RSA".equals(keyAlg)) {
            ext = ".RSA";
        } else if ("DSA".equals(keyAlg)) {
            ext = ".DSA";
        } else if ("EC".equals(keyAlg)) {
            ext = ".EC";
        } else {
            ext = ".SIG";
        }
        return "META-INF/SIGNATURE" + ext;
    }

    private void removeBlockFiles(Map<String, byte[]> entries) {
        entries.remove("META-INF/SIGNATURE.RSA");
        entries.remove("META-INF/SIGNATURE.DSA");
        entries.remove("META-INF/SIGNATURE.EC");
        entries.remove("META-INF/SIGNATURE.SIG");
    }

    static String base64Digest(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(DIGEST_ALGORITHM);
        byte[] digest = md.digest(data);
        return Base64.getEncoder().encodeToString(digest);
    }

    static List<ManifestSection> parseManifestSections(String manifestText) {
        List<ManifestSection> sections = new ArrayList<>();
        // Split on blank lines (CRLF or LF)
        String[] parts = manifestText.split("(?:\\r?\\n){2,}");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            ManifestSection section = new ManifestSection();
            // Re-add trailing newlines to match original section format
            section.rawText = trimmed + "\r\n";
            // Check if this section has a Name header
            for (String line : trimmed.split("\\r?\\n")) {
                if (line.startsWith("Name:")) {
                    section.name = line.substring(5).trim();
                    break;
                }
            }
            sections.add(section);
        }
        return sections;
    }

    private static String defaultSignatureAlgorithm(PrivateKey key) {
        String alg = key.getAlgorithm();
        if ("RSA".equals(alg)) return "SHA256withRSA";
        if ("DSA".equals(alg)) return "SHA256withDSA";
        if ("EC".equals(alg)) return "SHA256withECDSA";
        return "SHA256with" + alg;
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

    static class ManifestSection {
        String name; // null for main section
        String rawText;
    }
}
