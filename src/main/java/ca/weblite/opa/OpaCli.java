package ca.weblite.opa;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

/**
 * Command-line interface for working with OPA (Open Prompt Archive) files.
 *
 * <p>Commands:</p>
 * <pre>
 * opa create   -p prompt.md [-o out.opa] [-t title] [--data dir/] ...
 * opa inspect  archive.opa
 * opa extract  archive.opa [-o dir/]
 * opa sign     archive.opa --key key.pem --cert cert.pem [-o signed.opa]
 * opa verify   archive.opa [--cert cert.pem]
 * </pre>
 */
public class OpaCli {

    private static final String VERSION = "0.1.0";

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        String command = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);

        try {
            switch (command) {
                case "create":
                    doCreate(rest);
                    break;
                case "inspect":
                    doInspect(rest);
                    break;
                case "extract":
                    doExtract(rest);
                    break;
                case "sign":
                    doSign(rest);
                    break;
                case "verify":
                    doVerify(rest);
                    break;
                case "help":
                case "--help":
                case "-h":
                    printUsage();
                    break;
                case "version":
                case "--version":
                case "-v":
                    System.out.println("opa " + VERSION);
                    break;
                default:
                    System.err.println("Unknown command: " + command);
                    printUsage();
                    System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    // ---- create ----

    private static void doCreate(String[] args) throws Exception {
        String promptFile = null;
        String promptText = null;
        String output = null;
        String title = null;
        String description = null;
        String executionMode = null;
        String agentHint = null;
        String sessionFile = null;
        List<String> dataDirs = new ArrayList<>();
        List<String> dataFiles = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-p": case "--prompt":
                    promptFile = args[++i];
                    break;
                case "--prompt-text":
                    promptText = args[++i];
                    break;
                case "-o": case "--output":
                    output = args[++i];
                    break;
                case "-t": case "--title":
                    title = args[++i];
                    break;
                case "-d": case "--description":
                    description = args[++i];
                    break;
                case "--mode":
                    executionMode = args[++i];
                    break;
                case "--agent-hint":
                    agentHint = args[++i];
                    break;
                case "--session":
                    sessionFile = args[++i];
                    break;
                case "--data-dir":
                    dataDirs.add(args[++i]);
                    break;
                case "--data-file":
                    dataFiles.add(args[++i]);
                    break;
                case "--help": case "-h":
                    printCreateUsage();
                    return;
                default:
                    throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }

        if (promptFile == null && promptText == null) {
            System.err.println("Error: --prompt or --prompt-text is required.");
            printCreateUsage();
            System.exit(1);
        }

        String prompt;
        if (promptText != null) {
            prompt = promptText;
        } else {
            prompt = new String(Files.readAllBytes(Paths.get(promptFile)), StandardCharsets.UTF_8);
        }

        if (output == null) {
            if (title != null) {
                output = title.toLowerCase().replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("^-|-$", "") + ".opa";
            } else if (promptFile != null) {
                String base = Paths.get(promptFile).getFileName().toString();
                int dot = base.lastIndexOf('.');
                output = (dot > 0 ? base.substring(0, dot) : base) + ".opa";
            } else {
                output = "output.opa";
            }
        }

        OpaManifest manifest = new OpaManifest();
        if (title != null) manifest.setTitle(title);
        if (description != null) manifest.setDescription(description);
        if (agentHint != null) manifest.setAgentHint(agentHint);
        if (executionMode != null) manifest.setExecutionMode(ExecutionMode.fromValue(executionMode));
        manifest.setCreatedBy("opa-cli " + VERSION);

        OpaWriter writer = new OpaWriter();
        writer.setManifest(manifest);
        writer.setPrompt(prompt);

        // Session history
        if (sessionFile != null) {
            try (InputStream is = new FileInputStream(sessionFile)) {
                writer.setSessionHistory(SessionHistoryJson.parse(is));
            }
        }

        // Data directories
        DataIndex dataIndex = null;
        for (String dir : dataDirs) {
            File dirFile = new File(dir);
            if (!dirFile.isDirectory()) {
                throw new IllegalArgumentException("Not a directory: " + dir);
            }
            writer.addDataDirectory(dirFile, "data/");
            // Auto-populate data index
            dataIndex = (dataIndex != null) ? dataIndex : new DataIndex();
            DataIndex finalDataIndex = dataIndex;
            Files.walkFileTree(dirFile.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String rel = dirFile.toPath().relativize(file).toString().replace('\\', '/');
                    String mime = guessMimeType(file.getFileName().toString());
                    finalDataIndex.addAsset("data/" + rel, null, mime);
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        // Individual data files: --data-file path/to/file or --data-file archivePath=localPath
        for (String spec : dataFiles) {
            String archivePath;
            String localPath;
            int eq = spec.indexOf('=');
            if (eq > 0) {
                archivePath = spec.substring(0, eq);
                localPath = spec.substring(eq + 1);
            } else {
                localPath = spec;
                archivePath = "data/" + Paths.get(spec).getFileName().toString();
            }
            byte[] content = Files.readAllBytes(Paths.get(localPath));
            writer.addDataFile(archivePath, content);
            dataIndex = (dataIndex != null) ? dataIndex : new DataIndex();
            dataIndex.addAsset(archivePath, null, guessMimeType(localPath));
        }

        if (dataIndex != null) {
            writer.setDataIndex(dataIndex);
        }

        writer.writeTo(new File(output));
        System.out.println("Created " + output);
    }

    // ---- inspect ----

    private static void doInspect(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: opa inspect <archive.opa>");
            System.exit(1);
        }
        String archivePath = args[0];
        File file = new File(archivePath);
        OpaArchive archive = OpaReader.read(file);
        OpaManifest m = archive.getManifest();

        System.out.println("Archive: " + file.getName());
        System.out.println("  Size: " + formatSize(file.length()));
        System.out.println();

        // Manifest attributes
        System.out.println("Manifest:");
        for (Map.Entry<String, String> attr : m.getAttributes().entrySet()) {
            System.out.println("  " + attr.getKey() + ": " + attr.getValue());
        }
        System.out.println();

        // Prompt (first few lines)
        System.out.println("Prompt:");
        String[] promptLines = archive.getPrompt().split("\\r?\\n");
        int maxLines = Math.min(promptLines.length, 10);
        for (int i = 0; i < maxLines; i++) {
            System.out.println("  " + promptLines[i]);
        }
        if (promptLines.length > maxLines) {
            System.out.println("  ... (" + (promptLines.length - maxLines) + " more lines)");
        }
        System.out.println();

        // Session history
        if (archive.getSessionHistory() != null) {
            SessionHistory sh = archive.getSessionHistory();
            System.out.println("Session History:");
            System.out.println("  Session ID: " + sh.getSessionId());
            System.out.println("  Messages: " + sh.getMessages().size());
            if (sh.getCreatedAt() != null) System.out.println("  Created: " + sh.getCreatedAt());
            if (sh.getUpdatedAt() != null) System.out.println("  Updated: " + sh.getUpdatedAt());
            System.out.println();
        }

        // Data assets
        List<String> dataPaths = archive.getDataEntryPaths();
        if (!dataPaths.isEmpty()) {
            System.out.println("Data Assets (" + dataPaths.size() + "):");
            for (String path : dataPaths) {
                byte[] entry = archive.getEntry(path);
                System.out.println("  " + path + " (" + formatSize(entry.length) + ")");
            }
            System.out.println();
        }

        // Attachments
        List<String> attachments = archive.getAttachmentPaths();
        if (!attachments.isEmpty()) {
            System.out.println("Attachments (" + attachments.size() + "):");
            for (String path : attachments) {
                byte[] entry = archive.getEntry(path);
                System.out.println("  " + path + " (" + formatSize(entry.length) + ")");
            }
            System.out.println();
        }

        // Signature
        System.out.println("Signature:");
        if (!archive.isSigned()) {
            System.out.println("  Not signed");
        } else {
            OpaVerifier verifier = new OpaVerifier();
            try {
                OpaVerifier.Result result = verifier.verify(file);
                System.out.println("  Signed: yes");
                System.out.println("  Valid: " + result.isValid());
                if (result.getCertificates() != null && result.getCertificates().length > 0) {
                    Certificate cert = result.getCertificates()[0];
                    if (cert instanceof X509Certificate) {
                        X509Certificate x509 = (X509Certificate) cert;
                        System.out.println("  Signer: " + x509.getSubjectX500Principal());
                        System.out.println("  Issuer: " + x509.getIssuerX500Principal());
                        System.out.println("  Valid from: " + x509.getNotBefore());
                        System.out.println("  Valid until: " + x509.getNotAfter());
                        System.out.println("  Algorithm: " + x509.getSigAlgName());
                    }
                }
            } catch (OpaSignatureException e) {
                System.out.println("  Signed: yes");
                System.out.println("  INVALID: " + e.getMessage());
            }
        }

        // All entries
        System.out.println();
        System.out.println("All Entries (" + archive.getEntryPaths().size() + "):");
        for (String path : archive.getEntryPaths()) {
            byte[] entry = archive.getEntry(path);
            System.out.println("  " + path + " (" + formatSize(entry.length) + ")");
        }
    }

    // ---- extract ----

    private static void doExtract(String[] args) throws Exception {
        String archivePath = null;
        String outputDir = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-o": case "--output":
                    outputDir = args[++i];
                    break;
                case "--help": case "-h":
                    System.out.println("Usage: opa extract <archive.opa> [-o output-dir/]");
                    return;
                default:
                    if (args[i].startsWith("-")) {
                        throw new IllegalArgumentException("Unknown option: " + args[i]);
                    }
                    archivePath = args[i];
            }
        }

        if (archivePath == null) {
            System.err.println("Usage: opa extract <archive.opa> [-o output-dir/]");
            System.exit(1);
        }

        File file = new File(archivePath);
        OpaArchive archive = OpaReader.read(file);

        if (outputDir == null) {
            String base = file.getName();
            int dot = base.lastIndexOf('.');
            outputDir = (dot > 0 ? base.substring(0, dot) : base);
        }

        Path outPath = Paths.get(outputDir);
        int count = 0;
        for (String entryPath : archive.getEntryPaths()) {
            Path target = outPath.resolve(entryPath);
            Files.createDirectories(target.getParent());
            Files.write(target, archive.getEntry(entryPath));
            count++;
        }
        System.out.println("Extracted " + count + " entries to " + outputDir + "/");
    }

    // ---- sign ----

    private static void doSign(String[] args) throws Exception {
        String archivePath = null;
        String keyFile = null;
        String certFile = null;
        String output = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--key": case "-k":
                    keyFile = args[++i];
                    break;
                case "--cert": case "-c":
                    certFile = args[++i];
                    break;
                case "-o": case "--output":
                    output = args[++i];
                    break;
                case "--help": case "-h":
                    printSignUsage();
                    return;
                default:
                    if (args[i].startsWith("-")) {
                        throw new IllegalArgumentException("Unknown option: " + args[i]);
                    }
                    archivePath = args[i];
            }
        }

        if (archivePath == null || keyFile == null || certFile == null) {
            System.err.println("Error: archive, --key, and --cert are all required.");
            printSignUsage();
            System.exit(1);
        }

        PrivateKey privateKey = readPrivateKey(Paths.get(keyFile));
        Certificate cert = readCertificate(Paths.get(certFile));

        if (output == null) {
            output = archivePath; // overwrite in-place
        }

        File inputFile = new File(archivePath);
        File outputFile = new File(output);

        // If signing in-place, use a temp file
        boolean inPlace = inputFile.getCanonicalPath().equals(outputFile.getCanonicalPath());
        File actualOutput = inPlace ? File.createTempFile("opa-sign-", ".opa") : outputFile;

        OpaSigner signer = new OpaSigner(privateKey, new Certificate[]{cert});
        signer.setCreatedBy("opa-cli " + VERSION);
        signer.sign(inputFile, actualOutput);

        if (inPlace) {
            Files.copy(actualOutput.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            actualOutput.delete();
        }

        System.out.println("Signed " + archivePath + " -> " + output);
    }

    // ---- verify ----

    private static void doVerify(String[] args) throws Exception {
        String archivePath = null;
        String certFile = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--cert": case "-c":
                    certFile = args[++i];
                    break;
                case "--help": case "-h":
                    System.out.println("Usage: opa verify <archive.opa> [--cert trusted-cert.pem]");
                    return;
                default:
                    if (args[i].startsWith("-")) {
                        throw new IllegalArgumentException("Unknown option: " + args[i]);
                    }
                    archivePath = args[i];
            }
        }

        if (archivePath == null) {
            System.err.println("Usage: opa verify <archive.opa> [--cert trusted-cert.pem]");
            System.exit(1);
        }

        OpaVerifier verifier = new OpaVerifier();
        if (certFile != null) {
            Certificate cert = readCertificate(Paths.get(certFile));
            verifier.addTrustedCertificate(cert);
        }

        try {
            OpaVerifier.Result result = verifier.verify(new File(archivePath));
            if (!result.isSigned()) {
                System.out.println("NOT SIGNED");
                System.exit(1);
            }
            System.out.println("Signature: VALID");
            if (certFile != null) {
                System.out.println("Trusted: " + (result.isTrusted() ? "YES" : "NO"));
                if (!result.isTrusted()) {
                    System.exit(1);
                }
            }
            if (result.getCertificates() != null && result.getCertificates().length > 0) {
                Certificate c = result.getCertificates()[0];
                if (c instanceof X509Certificate) {
                    System.out.println("Signer: " + ((X509Certificate) c).getSubjectX500Principal());
                }
            }
        } catch (OpaSignatureException e) {
            System.out.println("Signature: INVALID");
            System.out.println("Reason: " + e.getMessage());
            System.exit(1);
        }
    }

    // ---- PEM reading ----

    static PrivateKey readPrivateKey(Path path) throws Exception {
        String pem = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        // Strip comments and PEM headers
        StringBuilder b64 = new StringBuilder();
        boolean inBlock = false;
        for (String line : pem.split("\\r?\\n")) {
            if (line.startsWith("-----BEGIN") && line.contains("PRIVATE KEY")) {
                inBlock = true;
                continue;
            }
            if (line.startsWith("-----END") && line.contains("PRIVATE KEY")) {
                break;
            }
            if (inBlock && !line.startsWith("#")) {
                b64.append(line.trim());
            }
        }
        byte[] der = Base64.getDecoder().decode(b64.toString());
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);

        // Try RSA first, then EC, then DSA
        String[] algorithms = {"RSA", "EC", "DSA"};
        for (String alg : algorithms) {
            try {
                return KeyFactory.getInstance(alg).generatePrivate(spec);
            } catch (Exception ignored) {
            }
        }
        throw new IllegalArgumentException("Could not parse private key from " + path +
                " (tried RSA, EC, DSA)");
    }

    static Certificate readCertificate(Path path) throws Exception {
        String pem = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        // Strip comments before feeding to CertificateFactory
        StringBuilder cleaned = new StringBuilder();
        for (String line : pem.split("\\r?\\n")) {
            if (!line.startsWith("#")) {
                cleaned.append(line).append("\n");
            }
        }
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return cf.generateCertificate(
                new ByteArrayInputStream(cleaned.toString().getBytes(StandardCharsets.UTF_8)));
    }

    // ---- help text ----

    private static void printUsage() {
        System.out.println("opa " + VERSION + " - Open Prompt Archive CLI");
        System.out.println();
        System.out.println("Usage: opa <command> [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  create    Create a new OPA archive");
        System.out.println("  inspect   Display archive contents and metadata");
        System.out.println("  extract   Extract archive entries to a directory");
        System.out.println("  sign      Sign an OPA archive");
        System.out.println("  verify    Verify an archive signature");
        System.out.println("  version   Print version");
        System.out.println("  help      Show this help");
        System.out.println();
        System.out.println("Run 'opa <command> --help' for command-specific help.");
    }

    private static void printCreateUsage() {
        System.out.println("Usage: opa create [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -p, --prompt <file>         Prompt file (e.g., prompt.md)");
        System.out.println("      --prompt-text <text>    Prompt as inline text");
        System.out.println("  -o, --output <file>         Output file (default: derived from title/prompt)");
        System.out.println("  -t, --title <title>         Archive title");
        System.out.println("  -d, --description <desc>    Archive description");
        System.out.println("      --mode <mode>           Execution mode: interactive, batch, autonomous");
        System.out.println("      --agent-hint <hint>     Agent hint (e.g., claude-sonnet)");
        System.out.println("      --session <file>        Session history JSON file");
        System.out.println("      --data-dir <dir>        Add directory contents as data/ assets");
        System.out.println("      --data-file <spec>      Add data file (path or archivePath=localPath)");
    }

    private static void printSignUsage() {
        System.out.println("Usage: opa sign <archive.opa> --key key.pem --cert cert.pem [-o signed.opa]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -k, --key <file>    Private key in PEM format (PKCS#8)");
        System.out.println("  -c, --cert <file>   Certificate in PEM format (X.509)");
        System.out.println("  -o, --output <file> Output file (default: overwrite input)");
    }

    // ---- utils ----

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private static String guessMimeType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".xml")) return "application/xml";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".md")) return "text/markdown";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }
}
