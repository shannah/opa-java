package ca.weblite.opa;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.math.BigInteger;
import java.util.Base64;
import java.util.Date;

import static org.junit.Assert.*;

public class OpaCliTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testCreateWithPromptFile() throws Exception {
        File promptFile = tempFolder.newFile("prompt.md");
        Files.write(promptFile.toPath(), "Analyse the data.".getBytes());

        File opaFile = new File(tempFolder.getRoot(), "test.opa");

        OpaCli.main(new String[]{
                "create",
                "-p", promptFile.getAbsolutePath(),
                "-o", opaFile.getAbsolutePath(),
                "-t", "Test Archive"
        });

        assertTrue(opaFile.exists());
        OpaArchive archive = OpaReader.read(opaFile);
        assertEquals("Analyse the data.", archive.getPrompt());
        assertEquals("Test Archive", archive.getManifest().getTitle());
    }

    @Test
    public void testCreateWithPromptText() throws Exception {
        File opaFile = new File(tempFolder.getRoot(), "inline.opa");

        OpaCli.main(new String[]{
                "create",
                "--prompt-text", "Hello world prompt.",
                "-o", opaFile.getAbsolutePath()
        });

        assertTrue(opaFile.exists());
        OpaArchive archive = OpaReader.read(opaFile);
        assertEquals("Hello world prompt.", archive.getPrompt());
    }

    @Test
    public void testCreateWithDataDir() throws Exception {
        File dataDir = tempFolder.newFolder("mydata");
        Files.write(new File(dataDir, "info.csv").toPath(), "a,b\n1,2".getBytes());

        File promptFile = tempFolder.newFile("prompt.md");
        Files.write(promptFile.toPath(), "Read the data.".getBytes());

        File opaFile = new File(tempFolder.getRoot(), "withdata.opa");

        OpaCli.main(new String[]{
                "create",
                "-p", promptFile.getAbsolutePath(),
                "-o", opaFile.getAbsolutePath(),
                "--data-dir", dataDir.getAbsolutePath()
        });

        OpaArchive archive = OpaReader.read(opaFile);
        assertNotNull(archive.getEntry("data/info.csv"));
        assertEquals("a,b\n1,2", new String(archive.getEntry("data/info.csv"), "UTF-8"));
        assertNotNull(archive.getDataIndex());
    }

    @Test
    public void testCreateWithDataFile() throws Exception {
        File dataFile = tempFolder.newFile("report.csv");
        Files.write(dataFile.toPath(), "x,y\n3,4".getBytes());

        File opaFile = new File(tempFolder.getRoot(), "withfile.opa");

        OpaCli.main(new String[]{
                "create",
                "--prompt-text", "Analyse report.",
                "-o", opaFile.getAbsolutePath(),
                "--data-file", "data/report.csv=" + dataFile.getAbsolutePath()
        });

        OpaArchive archive = OpaReader.read(opaFile);
        assertNotNull(archive.getEntry("data/report.csv"));
        assertEquals("x,y\n3,4", new String(archive.getEntry("data/report.csv"), "UTF-8"));
    }

    @Test
    public void testCreateWithExecutionMode() throws Exception {
        File opaFile = new File(tempFolder.getRoot(), "batch.opa");

        OpaCli.main(new String[]{
                "create",
                "--prompt-text", "Batch job.",
                "-o", opaFile.getAbsolutePath(),
                "--mode", "batch"
        });

        OpaArchive archive = OpaReader.read(opaFile);
        assertEquals(ExecutionMode.BATCH, archive.getManifest().getExecutionMode());
    }

    @Test
    public void testExtract() throws Exception {
        // Create an archive first
        File opaFile = new File(tempFolder.getRoot(), "extract.opa");
        OpaWriter writer = new OpaWriter();
        writer.getManifest().setTitle("Extract Test");
        writer.setPrompt("Extract me.");
        writer.addDataFile("data/file.txt", "extracted content");
        writer.writeTo(opaFile);

        File outputDir = new File(tempFolder.getRoot(), "extracted");

        OpaCli.main(new String[]{
                "extract",
                opaFile.getAbsolutePath(),
                "-o", outputDir.getAbsolutePath()
        });

        assertTrue(outputDir.exists());
        Path promptPath = outputDir.toPath().resolve("prompt.md");
        assertTrue(Files.exists(promptPath));
        assertEquals("Extract me.", new String(Files.readAllBytes(promptPath), "UTF-8"));

        Path dataPath = outputDir.toPath().resolve("data/file.txt");
        assertTrue(Files.exists(dataPath));
        assertEquals("extracted content", new String(Files.readAllBytes(dataPath), "UTF-8"));
    }

    @Test
    public void testSignAndVerify() throws Exception {
        // Generate keypair and cert, write PEM files
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair keyPair = kpg.generateKeyPair();
        Certificate cert = buildSelfSignedCert(keyPair);

        File keyFile = tempFolder.newFile("test-key.pem");
        writePem(keyFile.toPath(), "PRIVATE KEY", keyPair.getPrivate().getEncoded());

        File certFile = tempFolder.newFile("test-cert.pem");
        writePem(certFile.toPath(), "CERTIFICATE", cert.getEncoded());

        // Create unsigned archive
        File unsigned = new File(tempFolder.getRoot(), "unsigned.opa");
        OpaWriter writer = new OpaWriter();
        writer.setPrompt("Sign me.");
        writer.writeTo(unsigned);

        // Sign via CLI
        File signed = new File(tempFolder.getRoot(), "signed.opa");
        OpaCli.main(new String[]{
                "sign",
                unsigned.getAbsolutePath(),
                "--key", keyFile.getAbsolutePath(),
                "--cert", certFile.getAbsolutePath(),
                "-o", signed.getAbsolutePath()
        });

        assertTrue(signed.exists());

        // Verify signed archive is readable
        OpaArchive archive = OpaReader.read(signed);
        assertTrue(archive.isSigned());
        assertEquals("Sign me.", archive.getPrompt());

        // Verify via CLI (should not throw / exit)
        OpaCli.main(new String[]{
                "verify",
                signed.getAbsolutePath(),
                "--cert", certFile.getAbsolutePath()
        });
    }

    @Test
    public void testSignInPlace() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair keyPair = kpg.generateKeyPair();
        Certificate cert = buildSelfSignedCert(keyPair);

        File keyFile = tempFolder.newFile("key.pem");
        writePem(keyFile.toPath(), "PRIVATE KEY", keyPair.getPrivate().getEncoded());

        File certFile = tempFolder.newFile("cert.pem");
        writePem(certFile.toPath(), "CERTIFICATE", cert.getEncoded());

        File opaFile = new File(tempFolder.getRoot(), "inplace.opa");
        OpaWriter writer = new OpaWriter();
        writer.setPrompt("In-place signing.");
        writer.writeTo(opaFile);

        assertFalse(OpaReader.read(opaFile).isSigned());

        // Sign in place (no -o)
        OpaCli.main(new String[]{
                "sign",
                opaFile.getAbsolutePath(),
                "--key", keyFile.getAbsolutePath(),
                "--cert", certFile.getAbsolutePath()
        });

        assertTrue(OpaReader.read(opaFile).isSigned());
    }

    @Test
    public void testInspectOutput() throws Exception {
        File opaFile = new File(tempFolder.getRoot(), "inspect.opa");
        OpaWriter writer = new OpaWriter();
        writer.getManifest().setTitle("Inspect Test");
        writer.setPrompt("Inspect this archive.");
        writer.addDataFile("data/test.csv", "a,b\n1,2");
        writer.writeTo(opaFile);

        // Capture stdout
        PrintStream oldOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured));
        try {
            OpaCli.main(new String[]{"inspect", opaFile.getAbsolutePath()});
        } finally {
            System.setOut(oldOut);
        }

        String output = captured.toString("UTF-8");
        assertTrue(output.contains("Inspect Test"));
        assertTrue(output.contains("Inspect this archive."));
        assertTrue(output.contains("data/test.csv"));
        assertTrue(output.contains("Not signed"));
    }

    @Test
    public void testReadPemWithComments() throws Exception {
        // Verify that PEM reading works with comment lines (like our sample files)
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair keyPair = kpg.generateKeyPair();

        File keyFile = tempFolder.newFile("commented-key.pem");
        StringBuilder sb = new StringBuilder();
        sb.append("# SAMPLE KEY - FOR TESTING ONLY\n");
        sb.append("-----BEGIN PRIVATE KEY-----\n");
        sb.append(Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(keyPair.getPrivate().getEncoded()));
        sb.append("\n-----END PRIVATE KEY-----\n");
        Files.write(keyFile.toPath(), sb.toString().getBytes());

        PrivateKey loaded = OpaCli.readPrivateKey(keyFile.toPath());
        assertNotNull(loaded);
        assertEquals("RSA", loaded.getAlgorithm());
        assertArrayEquals(keyPair.getPrivate().getEncoded(), loaded.getEncoded());
    }

    // ---- helpers ----

    private void writePem(Path path, String type, byte[] der) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN ").append(type).append("-----\n");
        sb.append(Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der));
        sb.append("\n-----END ").append(type).append("-----\n");
        Files.write(path, sb.toString().getBytes());
    }

    private Certificate buildSelfSignedCert(KeyPair keyPair) throws Exception {
        byte[] name = encodeDN("CN=CLI Test");
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 86400000L);
        Date notAfter = new Date(now + 365L * 86400000L);

        byte[] serialNumber = BigInteger.valueOf(now).toByteArray();
        byte[] pubKeyEncoded = keyPair.getPublic().getEncoded();

        ByteArrayOutputStream tbs = new ByteArrayOutputStream();
        tbs.write(derTag(0xA0, derInteger(2)));
        tbs.write(derInteger(new BigInteger(1, serialNumber)));
        tbs.write(derAlgorithmIdentifier());
        tbs.write(name);
        tbs.write(derSequence(concat(derUTCTime(notBefore), derUTCTime(notAfter))));
        tbs.write(name);
        tbs.write(pubKeyEncoded);

        byte[] tbsBytes = derSequence(tbs.toByteArray());

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(keyPair.getPrivate());
        sig.update(tbsBytes);
        byte[] sigBytes = sig.sign();

        byte[] certDer = derSequence(concat(
                tbsBytes, derAlgorithmIdentifier(), derBitString(sigBytes)));

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return cf.generateCertificate(new ByteArrayInputStream(certDer));
    }

    private byte[] derTag(int tag, byte[] content) {
        return concat(new byte[]{(byte) tag}, derLength(content.length), content);
    }

    private byte[] derSequence(byte[] content) { return derTag(0x30, content); }

    private byte[] derInteger(int value) { return derInteger(BigInteger.valueOf(value)); }

    private byte[] derInteger(BigInteger value) { return derTag(0x02, value.toByteArray()); }

    private byte[] derBitString(byte[] content) {
        byte[] wrapped = new byte[content.length + 1];
        wrapped[0] = 0;
        System.arraycopy(content, 0, wrapped, 1, content.length);
        return derTag(0x03, wrapped);
    }

    private byte[] derUTCTime(Date date) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyMMddHHmmss'Z'");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return derTag(0x17, sdf.format(date).getBytes());
    }

    private byte[] derOID(int[] oid) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(oid[0] * 40 + oid[1]);
        for (int i = 2; i < oid.length; i++) encodeOIDComponent(out, oid[i]);
        return derTag(0x06, out.toByteArray());
    }

    private void encodeOIDComponent(ByteArrayOutputStream out, int value) {
        if (value < 128) { out.write(value); return; }
        byte[] bytes = new byte[5];
        int pos = 4;
        bytes[pos--] = (byte) (value & 0x7F);
        value >>= 7;
        while (value > 0) { bytes[pos--] = (byte) (0x80 | (value & 0x7F)); value >>= 7; }
        out.write(bytes, pos + 1, 4 - pos);
    }

    private byte[] derAlgorithmIdentifier() {
        return derSequence(concat(
                derOID(new int[]{1, 2, 840, 113549, 1, 1, 11}),
                new byte[]{0x05, 0x00}));
    }

    private byte[] encodeDN(String dn) {
        String cn = dn.substring(3);
        byte[] atv = derSequence(concat(
                derOID(new int[]{2, 5, 4, 3}), derTag(0x0C, cn.getBytes())));
        return derSequence(derTag(0x31, atv));
    }

    private byte[] derLength(int length) {
        if (length < 128) return new byte[]{(byte) length};
        if (length < 256) return new byte[]{(byte) 0x81, (byte) length};
        return new byte[]{(byte) 0x82, (byte) (length >> 8), (byte) length};
    }

    private byte[] concat(byte[]... arrays) {
        int len = 0; for (byte[] a : arrays) len += a.length;
        byte[] r = new byte[len]; int pos = 0;
        for (byte[] a : arrays) { System.arraycopy(a, 0, r, pos, a.length); pos += a.length; }
        return r;
    }
}
