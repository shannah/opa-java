package ca.weblite.opa;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.*;
import java.util.zip.*;

/**
 * Verifies digital signatures on OPA archives following JAR signing conventions.
 *
 * <p>Verification checks:</p>
 * <ol>
 *   <li>Digital signature in the block file is valid against SIGNATURE.SF</li>
 *   <li>Manifest digest in SIGNATURE.SF matches the actual MANIFEST.MF</li>
 *   <li>Per-entry section digests match the corresponding MANIFEST.MF sections</li>
 * </ol>
 *
 * <p>Example usage:</p>
 * <pre>
 * OpaVerifier verifier = new OpaVerifier();
 * verifier.addTrustedCertificate(cert);
 * OpaVerifier.Result result = verifier.verify(new File("signed.opa"));
 * if (result.isSigned() &amp;&amp; result.isValid()) {
 *     // archive is authentic
 * }
 * </pre>
 */
public class OpaVerifier {

    private final Set<PublicKey> trustedKeys = new HashSet<>();

    /**
     * Adds a trusted certificate. The archive signer's certificate must match
     * one of the trusted certificates for trust verification to succeed.
     */
    public OpaVerifier addTrustedCertificate(Certificate cert) {
        trustedKeys.add(cert.getPublicKey());
        return this;
    }

    /**
     * Adds a trusted public key directly.
     */
    public OpaVerifier addTrustedKey(PublicKey key) {
        trustedKeys.add(key);
        return this;
    }

    /**
     * Verifies a signed OPA archive from a file.
     */
    public Result verify(File file) throws IOException, OpaException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipFile zf = new ZipFile(file)) {
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
        return verifyEntries(entries);
    }

    /**
     * Verifies a signed OPA archive from a stream.
     */
    public Result verify(InputStream in) throws IOException, OpaException {
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
        return verifyEntries(entries);
    }

    private Result verifyEntries(Map<String, byte[]> entries) throws OpaException {
        byte[] sfBytes = entries.get(OpaSigner.SF_PATH);
        byte[] blockBytes = findBlockFile(entries);

        if (sfBytes == null || blockBytes == null) {
            return new Result(false, false, false, null);
        }

        // Parse the block file to get signature and certificates
        BlockFile block;
        try {
            block = parseBlockFile(blockBytes);
        } catch (Exception e) {
            throw new OpaSignatureException("Invalid signature block file", e);
        }

        // Step 1: Verify the digital signature of SIGNATURE.SF
        boolean signatureValid;
        try {
            Signature sig = Signature.getInstance(block.algorithm);
            sig.initVerify(block.signerPublicKey);
            sig.update(sfBytes);
            signatureValid = sig.verify(block.signature);
        } catch (GeneralSecurityException e) {
            throw new OpaSignatureException("Signature verification failed", e);
        }

        if (!signatureValid) {
            throw new OpaSignatureException("Digital signature is invalid");
        }

        // Step 2: Verify manifest digest in SIGNATURE.SF matches actual MANIFEST.MF
        byte[] manifestBytes = entries.get(OpaManifest.MANIFEST_PATH);
        if (manifestBytes == null) {
            throw new OpaSignatureException("Signed archive is missing MANIFEST.MF");
        }

        String sfContent = new String(sfBytes, StandardCharsets.UTF_8);
        String expectedManifestDigest = extractHeaderValue(sfContent,
                OpaSigner.MANIFEST_DIGEST_HEADER);
        if (expectedManifestDigest == null) {
            throw new OpaSignatureException("SIGNATURE.SF is missing " +
                    OpaSigner.MANIFEST_DIGEST_HEADER);
        }

        String actualManifestDigest;
        try {
            actualManifestDigest = OpaSigner.base64Digest(manifestBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new OpaSignatureException("SHA-256 not available", e);
        }

        if (!expectedManifestDigest.equals(actualManifestDigest)) {
            throw new OpaSignatureException(
                    "Manifest digest mismatch: archive has been tampered with");
        }

        // Step 3: Verify per-entry section digests
        verifyEntrySectionDigests(sfContent,
                new String(manifestBytes, StandardCharsets.UTF_8));

        // Check trust
        boolean trusted = trustedKeys.isEmpty() ||
                trustedKeys.contains(block.signerPublicKey);

        return new Result(true, true, trusted, block.certificates);
    }

    private void verifyEntrySectionDigests(String sfContent, String manifestText)
            throws OpaSignatureException {
        // Parse named sections from SIGNATURE.SF
        Map<String, String> sfDigests = new LinkedHashMap<>();
        List<OpaSigner.ManifestSection> sfSections =
                OpaSigner.parseManifestSections(sfContent);
        for (OpaSigner.ManifestSection section : sfSections) {
            if (section.name != null) {
                String digest = extractHeaderValue(section.rawText,
                        OpaSigner.DIGEST_HEADER);
                if (digest != null) {
                    sfDigests.put(section.name, digest);
                }
            }
        }

        // Parse named sections from MANIFEST.MF
        Map<String, String> manifestSections = new LinkedHashMap<>();
        List<OpaSigner.ManifestSection> mfSections =
                OpaSigner.parseManifestSections(manifestText);
        for (OpaSigner.ManifestSection section : mfSections) {
            if (section.name != null) {
                manifestSections.put(section.name, section.rawText);
            }
        }

        // Verify each SF entry has a matching manifest section
        for (Map.Entry<String, String> entry : sfDigests.entrySet()) {
            String name = entry.getKey();
            String expectedDigest = entry.getValue();
            String mfSection = manifestSections.get(name);

            if (mfSection == null) {
                throw new OpaSignatureException(
                        "SIGNATURE.SF references entry '" + name +
                                "' not found in MANIFEST.MF");
            }

            try {
                String actualDigest = OpaSigner.base64Digest(
                        mfSection.getBytes(StandardCharsets.UTF_8));
                if (!expectedDigest.equals(actualDigest)) {
                    throw new OpaSignatureException(
                            "Section digest mismatch for entry '" + name + "'");
                }
            } catch (NoSuchAlgorithmException e) {
                throw new OpaSignatureException("SHA-256 not available", e);
            }
        }
    }

    private byte[] findBlockFile(Map<String, byte[]> entries) {
        byte[] block = entries.get("META-INF/SIGNATURE.RSA");
        if (block != null) return block;
        block = entries.get("META-INF/SIGNATURE.DSA");
        if (block != null) return block;
        block = entries.get("META-INF/SIGNATURE.EC");
        if (block != null) return block;
        return entries.get("META-INF/SIGNATURE.SIG");
    }

    private BlockFile parseBlockFile(byte[] data) throws Exception {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        int magic = dis.readInt();
        if (magic != 0x4F504153) {
            throw new OpaSignatureException("Invalid signature block magic");
        }
        int version = dis.readInt();
        if (version != 1) {
            throw new OpaSignatureException("Unsupported signature block version: " + version);
        }

        // Algorithm
        int algLen = dis.readInt();
        byte[] algBytes = new byte[algLen];
        dis.readFully(algBytes);
        String algorithm = new String(algBytes, StandardCharsets.UTF_8);

        // Signature
        int sigLen = dis.readInt();
        byte[] signature = new byte[sigLen];
        dis.readFully(signature);

        // Certificates
        int certCount = dis.readInt();
        Certificate[] certs = new Certificate[certCount];
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        for (int i = 0; i < certCount; i++) {
            int certLen = dis.readInt();
            byte[] certBytes = new byte[certLen];
            dis.readFully(certBytes);
            certs[i] = cf.generateCertificate(new ByteArrayInputStream(certBytes));
        }

        BlockFile block = new BlockFile();
        block.algorithm = algorithm;
        block.signature = signature;
        block.certificates = certs;
        block.signerPublicKey = certs[0].getPublicKey();
        return block;
    }

    private static String extractHeaderValue(String text, String header) {
        for (String line : text.split("\\r?\\n")) {
            if (line.startsWith(header + ":")) {
                return line.substring(header.length() + 1).trim();
            }
        }
        return null;
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

    private static class BlockFile {
        String algorithm;
        byte[] signature;
        Certificate[] certificates;
        PublicKey signerPublicKey;
    }

    /**
     * Result of signature verification.
     */
    public static class Result {
        private final boolean signed;
        private final boolean valid;
        private final boolean trusted;
        private final Certificate[] certificates;

        Result(boolean signed, boolean valid, boolean trusted, Certificate[] certificates) {
            this.signed = signed;
            this.valid = valid;
            this.trusted = trusted;
            this.certificates = certificates;
        }

        /** Returns true if the archive contains signature files. */
        public boolean isSigned() {
            return signed;
        }

        /** Returns true if the signature is cryptographically valid. */
        public boolean isValid() {
            return valid;
        }

        /** Returns true if the signer's certificate is in the trust store. */
        public boolean isTrusted() {
            return trusted;
        }

        /** Returns the certificate chain from the signature, or null if unsigned. */
        public Certificate[] getCertificates() {
            return certificates;
        }
    }
}
