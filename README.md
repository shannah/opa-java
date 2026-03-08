# opa-java

Java library for reading, writing, signing, and verifying [Open Prompt Archive (OPA)](https://github.com/shannah/opa-spec) files.

OPA is a ZIP-based archive format for packaging AI prompts with their associated data, session history, and metadata. It follows JAR conventions for both manifest and digital signature formats.

**Zero external dependencies** — requires only Java 11+.

## Features

- Create, read, inspect, and extract OPA archives
- Digital signing and verification (RSA, DSA, EC) using JAR/PKCS#7 conventions
- Multi-turn session history with rich content blocks (text, images, tool use)
- Data asset management with directory support
- CLI tool for all operations
- Stream and file-based I/O

## Quick Start

### Maven

```xml
<dependency>
    <groupId>ca.weblite</groupId>
    <artifactId>opa-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### Creating an Archive

```java
OpaManifest manifest = new OpaManifest();
manifest.setTitle("My Prompt");
manifest.setDescription("Summarise a document");
manifest.setExecutionMode(ExecutionMode.INTERACTIVE);

OpaWriter writer = new OpaWriter();
writer.setManifest(manifest);
writer.setPrompt("You are a helpful assistant. Summarise the document in data/doc.txt.");
writer.addDataFile("data/doc.txt", "Contents of the document...");
writer.writeTo(new File("my-prompt.opa"));
```

### Reading an Archive

```java
OpaArchive archive = OpaReader.read(new File("my-prompt.opa"));

System.out.println(archive.getManifest().getTitle());
System.out.println(archive.getPrompt());

for (String path : archive.getDataEntryPaths()) {
    byte[] data = archive.getEntry(path);
    // ...
}
```

### Signing and Verifying

```java
// Sign
PrivateKey key = ...;
Certificate[] chain = ...;
OpaSigner signer = new OpaSigner(key, chain);
signer.sign(new File("unsigned.opa"), new File("signed.opa"));

// Verify
OpaVerifier verifier = new OpaVerifier();
verifier.addTrustedCertificate(chain[0]);
OpaVerifier.Result result = verifier.verify(new File("signed.opa"));

if (result.isSigned() && result.isValid() && result.isTrusted()) {
    // archive is authentic and from a trusted signer
}
```

### Session History

```java
SessionHistory history = new SessionHistory("session-001");
history.addMessage(new Message(MessageRole.USER, "Summarise this document"));
history.addMessage(new Message(MessageRole.ASSISTANT, "Here is a summary..."));

OpaWriter writer = new OpaWriter();
writer.setManifest(manifest);
writer.setPrompt("You are a helpful assistant.");
writer.setSessionHistory(history);
writer.writeTo(new File("with-session.opa"));
```

## CLI

Build the project, then run via `java -cp target/classes ca.weblite.opa.OpaCli` (or `java -jar target/opa-core-0.1.0-SNAPSHOT.jar`).

### Create

```bash
opa create \
    --prompt prompt.md \
    --title "My Prompt" \
    --description "Does something useful" \
    --mode interactive \
    --data-dir ./data \
    --output my-prompt.opa
```

Options:
- `-p, --prompt <file>` — prompt file
- `--prompt-text <text>` — inline prompt text
- `-o, --output <file>` — output path
- `-t, --title <title>` — archive title
- `-d, --description <desc>` — description
- `--mode <mode>` — `interactive`, `batch`, or `autonomous`
- `--agent-hint <hint>` — e.g. `claude-sonnet`
- `--session <file>` — session history JSON
- `--data-dir <dir>` — add directory as data assets
- `--data-file <spec>` — add a data file (`path` or `archivePath=localPath`)

### Sign

```bash
opa sign my-prompt.opa --key private.pem --cert certificate.pem
```

### Verify

```bash
opa verify my-prompt.opa --cert trusted-cert.pem
```

### Inspect

```bash
opa inspect my-prompt.opa
```

### Extract

```bash
opa extract my-prompt.opa --output ./extracted
```

## Archive Format

An OPA file is a standard ZIP archive:

```
META-INF/
    MANIFEST.MF            # JAR-style manifest with OPA metadata
    SIGNATURE.SF           # (if signed) signature file with digests
    SIGNATURE.RSA          # (if signed) PKCS#7 SignedData block
prompt.md                  # main prompt content
data/                      # data assets
    INDEX.json             # asset metadata
    feed.xml               # example data file
session/
    history.json           # conversation history
    attachments/           # binary attachments referenced in messages
```

The manifest follows JAR format:

```
Manifest-Version: 1.0
OPA-Version: 0.1
Title: My Prompt
Description: Does something useful
Execution-Mode: interactive
Created-By: opa-java
```

## Samples

The `samples/` directory contains:

- **`sample-key.pem`** / **`sample-cert.pem`** — test RSA keypair (not for production use)
- **`ai-feed-summary.opa`** — signed example that summarises a Hacker News feed
- **`pirate-news/`** — complete example with a pirate-themed news summarizer
  - `prompt.md` — prompt that generates an HTML news digest in pirate speak
  - `data/feed.xml` — sample RSS feed
  - `generate-opa.sh` — script to build, sign, and verify the archive

Run the pirate-news example:

```bash
cd samples/pirate-news
./generate-opa.sh
```

## Building

```bash
# Compile
javac --release 11 -d target/classes src/main/java/ca/weblite/opa/*.java

# Or with Maven
mvn package
```

## License

MIT
