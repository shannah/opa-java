package ca.weblite.opa;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.math.BigInteger;
import java.util.Date;

import static org.junit.Assert.*;

public class OpaSigningTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testSignAndVerify() throws Exception {
        KeyPair keyPair = generateKeyPair();
        java.security.cert.Certificate cert = generateSelfSignedCert(keyPair);

        // Create unsigned archive
        File unsigned = tempFolder.newFile("unsigned.opa");
        OpaWriter writer = new OpaWriter();
        writer.getManifest().setTitle("Signing Test");
        writer.setPrompt("Test prompt for signing.");
        writer.addDataFile("data/report.csv", "a,b\n1,2\n");
        writer.writeTo(unsigned);

        // Sign it
        File signed = tempFolder.newFile("signed.opa");
        OpaSigner signer = new OpaSigner(keyPair.getPrivate(),
                new java.security.cert.Certificate[]{cert});
        signer.sign(unsigned, signed);

        // Verify it
        OpaVerifier verifier = new OpaVerifier();
        verifier.addTrustedCertificate(cert);
        OpaVerifier.Result result = verifier.verify(signed);
        assertTrue("Archive should be signed", result.isSigned());
        assertTrue("Signature should be valid", result.isValid());
        assertTrue("Signer should be trusted", result.isTrusted());
        assertNotNull("Certificates should be present", result.getCertificates());
        assertEquals(1, result.getCertificates().length);
    }

    @Test
    public void testUnsignedArchiveDetectedBySigner() throws Exception {
        File unsigned = tempFolder.newFile("unsigned.opa");
        OpaWriter writer = new OpaWriter();
        writer.setPrompt("Test prompt.");
        writer.writeTo(unsigned);

        OpaVerifier verifier = new OpaVerifier();
        OpaVerifier.Result result = verifier.verify(unsigned);
        assertFalse("Unsigned archive should not be signed", result.isSigned());
        assertFalse("Unsigned archive should not be valid", result.isValid());
        assertFalse("Unsigned archive should not be trusted", result.isTrusted());
    }

    @Test
    public void testSignedArchiveReadable() throws Exception {
        KeyPair keyPair = generateKeyPair();
        java.security.cert.Certificate cert = generateSelfSignedCert(keyPair);

        File unsigned = tempFolder.newFile("unsigned.opa");
        OpaWriter writer = new OpaWriter();
        writer.getManifest().setTitle("Readable After Signing");
        writer.setPrompt("Signed prompt content.");
        writer.addDataFile("data/test.txt", "hello");
        writer.writeTo(unsigned);

        File signed = tempFolder.newFile("signed.opa");
        new OpaSigner(keyPair.getPrivate(),
                new java.security.cert.Certificate[]{cert}).sign(unsigned, signed);

        // Should still be readable by OpaReader
        OpaArchive archive = OpaReader.read(signed);
        assertEquals("Readable After Signing", archive.getManifest().getTitle());
        assertEquals("Signed prompt content.", archive.getPrompt());
        assertTrue("Archive should report signed", archive.isSigned());
        assertNotNull(archive.getEntry("data/test.txt"));
        assertEquals("hello", new String(archive.getEntry("data/test.txt"), "UTF-8"));
    }

    @Test
    public void testUntrustedSignerDetected() throws Exception {
        KeyPair signerKeyPair = generateKeyPair();
        java.security.cert.Certificate signerCert = generateSelfSignedCert(signerKeyPair);

        KeyPair trustedKeyPair = generateKeyPair();
        java.security.cert.Certificate trustedCert = generateSelfSignedCert(trustedKeyPair);

        File unsigned = tempFolder.newFile("unsigned.opa");
        OpaWriter writer = new OpaWriter();
        writer.setPrompt("Untrusted test.");
        writer.writeTo(unsigned);

        File signed = tempFolder.newFile("signed.opa");
        new OpaSigner(signerKeyPair.getPrivate(),
                new java.security.cert.Certificate[]{signerCert}).sign(unsigned, signed);

        // Verify with a different trusted cert
        OpaVerifier verifier = new OpaVerifier();
        verifier.addTrustedCertificate(trustedCert);
        OpaVerifier.Result result = verifier.verify(signed);
        assertTrue("Archive should be signed", result.isSigned());
        assertTrue("Signature should be valid", result.isValid());
        assertFalse("Signer should NOT be trusted", result.isTrusted());
    }

    @Test(expected = OpaSignatureException.class)
    public void testTamperedManifestDetected() throws Exception {
        KeyPair keyPair = generateKeyPair();
        java.security.cert.Certificate cert = generateSelfSignedCert(keyPair);

        File unsigned = tempFolder.newFile("unsigned.opa");
        OpaWriter writer = new OpaWriter();
        writer.setPrompt("Tamper test.");
        writer.writeTo(unsigned);

        File signed = tempFolder.newFile("signed.opa");
        new OpaSigner(keyPair.getPrivate(),
                new java.security.cert.Certificate[]{cert}).sign(unsigned, signed);

        // Tamper with the manifest by re-writing the archive with modified manifest
        File tampered = tempFolder.newFile("tampered.opa");
        tamperWithManifest(signed, tampered);

        // Verification should fail
        OpaVerifier verifier = new OpaVerifier();
        verifier.addTrustedCertificate(cert);
        verifier.verify(tampered);
    }

    @Test
    public void testSignViaStreams() throws Exception {
        KeyPair keyPair = generateKeyPair();
        java.security.cert.Certificate cert = generateSelfSignedCert(keyPair);

        File unsigned = tempFolder.newFile("unsigned.opa");
        OpaWriter writer = new OpaWriter();
        writer.setPrompt("Stream signing test.");
        writer.writeTo(unsigned);

        File signed = tempFolder.newFile("signed.opa");
        OpaSigner signer = new OpaSigner(keyPair.getPrivate(),
                new java.security.cert.Certificate[]{cert});
        try (FileInputStream fis = new FileInputStream(unsigned);
             FileOutputStream fos = new FileOutputStream(signed)) {
            signer.sign(fis, fos);
        }

        OpaVerifier verifier = new OpaVerifier();
        verifier.addTrustedCertificate(cert);
        OpaVerifier.Result result = verifier.verify(signed);
        assertTrue(result.isSigned());
        assertTrue(result.isValid());
    }

    @Test
    public void testVerifyViaStream() throws Exception {
        KeyPair keyPair = generateKeyPair();
        java.security.cert.Certificate cert = generateSelfSignedCert(keyPair);

        File unsigned = tempFolder.newFile("unsigned.opa");
        OpaWriter writer = new OpaWriter();
        writer.setPrompt("Stream verify test.");
        writer.writeTo(unsigned);

        File signed = tempFolder.newFile("signed.opa");
        new OpaSigner(keyPair.getPrivate(),
                new java.security.cert.Certificate[]{cert}).sign(unsigned, signed);

        OpaVerifier verifier = new OpaVerifier();
        verifier.addTrustedCertificate(cert);
        try (FileInputStream fis = new FileInputStream(signed)) {
            OpaVerifier.Result result = verifier.verify(fis);
            assertTrue(result.isSigned());
            assertTrue(result.isValid());
        }
    }

    @Test
    public void testEmptyTrustStoreAcceptsAll() throws Exception {
        KeyPair keyPair = generateKeyPair();
        java.security.cert.Certificate cert = generateSelfSignedCert(keyPair);

        File unsigned = tempFolder.newFile("unsigned.opa");
        OpaWriter writer = new OpaWriter();
        writer.setPrompt("No trust store test.");
        writer.writeTo(unsigned);

        File signed = tempFolder.newFile("signed.opa");
        new OpaSigner(keyPair.getPrivate(),
                new java.security.cert.Certificate[]{cert}).sign(unsigned, signed);

        // Empty trust store should report trusted (no restrictions)
        OpaVerifier verifier = new OpaVerifier();
        OpaVerifier.Result result = verifier.verify(signed);
        assertTrue(result.isSigned());
        assertTrue(result.isValid());
        assertTrue("Empty trust store should accept any valid signature", result.isTrusted());
    }

    @Test
    public void testUnsignedArchiveNotReportedSigned() throws Exception {
        File unsigned = tempFolder.newFile("unsigned.opa");
        OpaWriter writer = new OpaWriter();
        writer.setPrompt("Not signed.");
        writer.writeTo(unsigned);

        OpaArchive archive = OpaReader.read(unsigned);
        assertFalse("Unsigned archive should report not signed", archive.isSigned());
    }

    @Test
    public void testEcKeySignAndVerify() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);
        KeyPair keyPair = kpg.generateKeyPair();
        java.security.cert.Certificate cert = generateSelfSignedCertEc(keyPair);

        File unsigned = tempFolder.newFile("unsigned.opa");
        OpaWriter writer = new OpaWriter();
        writer.setPrompt("EC key test.");
        writer.writeTo(unsigned);

        File signed = tempFolder.newFile("signed.opa");
        new OpaSigner(keyPair.getPrivate(),
                new java.security.cert.Certificate[]{cert}).sign(unsigned, signed);

        OpaVerifier verifier = new OpaVerifier();
        verifier.addTrustedCertificate(cert);
        OpaVerifier.Result result = verifier.verify(signed);
        assertTrue(result.isSigned());
        assertTrue(result.isValid());
        assertTrue(result.isTrusted());
    }

    // --- Helper methods ---

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }

    /**
     * Generates a minimal self-signed X.509 certificate using only JDK APIs.
     */
    private java.security.cert.Certificate generateSelfSignedCert(KeyPair keyPair) throws Exception {
        return buildSelfSignedCert(keyPair, "SHA256withRSA");
    }

    private java.security.cert.Certificate generateSelfSignedCertEc(KeyPair keyPair) throws Exception {
        return buildSelfSignedCert(keyPair, "SHA256withECDSA");
    }

    private java.security.cert.Certificate buildSelfSignedCert(KeyPair keyPair, String sigAlg) throws Exception {
        // Build a minimal DER-encoded X.509 certificate
        byte[] name = encodeDN("CN=OPA Test");
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 86400000L);
        Date notAfter = new Date(now + 365L * 86400000L);

        byte[] serialNumber = BigInteger.valueOf(now).toByteArray();
        byte[] pubKeyEncoded = keyPair.getPublic().getEncoded();

        // Build TBSCertificate
        ByteArrayOutputStream tbs = new ByteArrayOutputStream();
        // version [0] EXPLICIT INTEGER v3
        tbs.write(derTag(0xA0, derInteger(2)));
        // serialNumber
        tbs.write(derInteger(new BigInteger(1, serialNumber)));
        // signature algorithm
        tbs.write(derAlgorithmIdentifier(sigAlg));
        // issuer
        tbs.write(name);
        // validity
        tbs.write(derSequence(concat(derUTCTime(notBefore), derUTCTime(notAfter))));
        // subject
        tbs.write(name);
        // subjectPublicKeyInfo (already DER)
        tbs.write(pubKeyEncoded);

        byte[] tbsBytes = derSequence(tbs.toByteArray());

        // Sign the TBSCertificate
        Signature sig = Signature.getInstance(sigAlg);
        sig.initSign(keyPair.getPrivate());
        sig.update(tbsBytes);
        byte[] sigBytes = sig.sign();

        // Build Certificate: SEQUENCE { tbsCertificate, signatureAlgorithm, signatureValue }
        byte[] certDer = derSequence(concat(
                tbsBytes,
                derAlgorithmIdentifier(sigAlg),
                derBitString(sigBytes)
        ));

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return cf.generateCertificate(new ByteArrayInputStream(certDer));
    }

    // --- ASN.1 DER helpers ---

    private byte[] derTag(int tag, byte[] content) {
        return concat(new byte[]{(byte) tag}, derLength(content.length), content);
    }

    private byte[] derSequence(byte[] content) {
        return derTag(0x30, content);
    }

    private byte[] derInteger(int value) {
        return derInteger(BigInteger.valueOf(value));
    }

    private byte[] derInteger(BigInteger value) {
        byte[] bytes = value.toByteArray();
        return derTag(0x02, bytes);
    }

    private byte[] derBitString(byte[] content) {
        byte[] wrapped = new byte[content.length + 1];
        wrapped[0] = 0; // no unused bits
        System.arraycopy(content, 0, wrapped, 1, content.length);
        return derTag(0x03, wrapped);
    }

    private byte[] derOctetString(byte[] content) {
        return derTag(0x04, content);
    }

    private byte[] derUTCTime(Date date) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyMMddHHmmss'Z'");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        byte[] timeBytes = sdf.format(date).getBytes();
        return derTag(0x17, timeBytes);
    }

    private byte[] derOID(int[] oid) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(oid[0] * 40 + oid[1]);
        for (int i = 2; i < oid.length; i++) {
            encodeOIDComponent(out, oid[i]);
        }
        return derTag(0x06, out.toByteArray());
    }

    private void encodeOIDComponent(ByteArrayOutputStream out, int value) {
        if (value < 128) {
            out.write(value);
        } else {
            // Multi-byte encoding
            byte[] bytes = new byte[5];
            int pos = 4;
            bytes[pos--] = (byte) (value & 0x7F);
            value >>= 7;
            while (value > 0) {
                bytes[pos--] = (byte) (0x80 | (value & 0x7F));
                value >>= 7;
            }
            out.write(bytes, pos + 1, 4 - pos);
        }
    }

    private byte[] derAlgorithmIdentifier(String alg) {
        int[] oid;
        if ("SHA256withRSA".equals(alg)) {
            oid = new int[]{1, 2, 840, 113549, 1, 1, 11};
        } else if ("SHA256withECDSA".equals(alg)) {
            oid = new int[]{1, 2, 840, 10045, 4, 3, 2};
        } else {
            throw new IllegalArgumentException("Unsupported algorithm: " + alg);
        }
        return derSequence(concat(derOID(oid), new byte[]{0x05, 0x00})); // NULL params
    }

    private byte[] encodeDN(String dn) {
        // Parse "CN=value"
        String cn = dn.substring(3);
        byte[] cnBytes = cn.getBytes();
        byte[] cnOid = derOID(new int[]{2, 5, 4, 3});
        byte[] cnValue = derTag(0x0C, cnBytes); // UTF8String
        byte[] atv = derSequence(concat(cnOid, cnValue));
        byte[] rdn = derTag(0x31, atv); // SET
        return derSequence(rdn);
    }

    private byte[] derLength(int length) {
        if (length < 128) {
            return new byte[]{(byte) length};
        } else if (length < 256) {
            return new byte[]{(byte) 0x81, (byte) length};
        } else if (length < 65536) {
            return new byte[]{(byte) 0x82, (byte) (length >> 8), (byte) length};
        } else {
            return new byte[]{(byte) 0x83, (byte) (length >> 16), (byte) (length >> 8), (byte) length};
        }
    }

    private byte[] concat(byte[]... arrays) {
        int totalLen = 0;
        for (byte[] a : arrays) totalLen += a.length;
        byte[] result = new byte[totalLen];
        int pos = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, result, pos, a.length);
            pos += a.length;
        }
        return result;
    }

    private void tamperWithManifest(File signed, File tampered) throws Exception {
        java.util.Map<String, byte[]> entries = new java.util.LinkedHashMap<>();
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(signed)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                java.util.zip.ZipEntry ze = en.nextElement();
                if (!ze.isDirectory()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try (InputStream is = zf.getInputStream(ze)) {
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
                    }
                    byte[] data = baos.toByteArray();
                    // Tamper with manifest by changing the OPA-Version
                    if (ze.getName().equals("META-INF/MANIFEST.MF")) {
                        String manifest = new String(data, "UTF-8");
                        manifest = manifest.replace("OPA-Version: 0.1", "OPA-Version: 9.9");
                        data = manifest.getBytes("UTF-8");
                    }
                    entries.put(ze.getName(), data);
                }
            }
        }
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                new FileOutputStream(tampered))) {
            for (java.util.Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zos.putNextEntry(new java.util.zip.ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
    }
}
