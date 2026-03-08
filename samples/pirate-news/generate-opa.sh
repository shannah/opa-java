#!/usr/bin/env bash
#
# generate-opa.sh — Build a signed OPA archive from the pirate-news sample.
#
# Produces: pirate-news.opa (signed with the sample keypair)
#
# Usage:
#   cd samples/pirate-news
#   ./generate-opa.sh
#
# Prerequisites:
#   - Java 11+
#   - The project must be compiled first:
#       cd ../.. && mvn package -DskipTests && cd samples/pirate-news
#     OR, if compiling manually:
#       javac -d ../../target/classes ../../src/main/java/ca/weblite/opa/*.java

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SAMPLES_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT="$SCRIPT_DIR/pirate-news.opa"

# Locate compiled classes
CLASSES_DIR="$PROJECT_ROOT/target/classes"
if [ ! -d "$CLASSES_DIR" ] || [ -z "$(ls "$CLASSES_DIR" 2>/dev/null)" ]; then
    echo "Compiled classes not found at $CLASSES_DIR"
    echo "Building project..."
    javac --release 11 -d "$CLASSES_DIR" "$PROJECT_ROOT"/src/main/java/ca/weblite/opa/*.java
fi

OPA="java -cp $CLASSES_DIR ca.weblite.opa.OpaCli"

echo "==> Creating unsigned archive..."
$OPA create \
    --prompt "$SCRIPT_DIR/prompt.md" \
    --title "Pirate News Digest" \
    --description "Summarise headline news in pirate speak and pick the top AI story." \
    --mode interactive \
    --data-dir "$SCRIPT_DIR/data" \
    --output "$OUTPUT"

echo "==> Signing with sample keypair..."
$OPA sign "$OUTPUT" \
    --key "$SAMPLES_DIR/sample-key.pem" \
    --cert "$SAMPLES_DIR/sample-cert.pem"

echo "==> Verifying..."
$OPA verify "$OUTPUT" --cert "$SAMPLES_DIR/sample-cert.pem"

echo ""
echo "==> Inspecting archive..."
$OPA inspect "$OUTPUT"

echo ""
echo "Done: $OUTPUT"
