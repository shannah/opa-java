#!/usr/bin/env bash
#
# generate-opa.sh — Build an UNSIGNED OPA archive from the code-review sample.
#
# Produces: code-review.opa (unsigned, suitable for testing over secure HTTP)
#
# Usage:
#   cd samples/code-review
#   ./generate-opa.sh
#
# Prerequisites:
#   - Java 11+
#   - The project must be compiled first:
#       cd ../.. && mvn package -DskipTests && cd samples/code-review

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUTPUT="$SCRIPT_DIR/code-review.opa"

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
    --title "Code Review Helper" \
    --description "Review a code diff for correctness, security issues, and style." \
    --mode batch \
    --data-dir "$SCRIPT_DIR/data" \
    --output "$OUTPUT"

echo ""
echo "==> Inspecting archive..."
$OPA inspect "$OUTPUT"

echo ""
echo "Done: $OUTPUT (unsigned)"
