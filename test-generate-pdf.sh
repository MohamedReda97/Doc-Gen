#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
  cat <<'USAGE'
Usage:
  ./test-generate-pdf.sh <template.docx> <data.json> [output.pdf]

Examples:
  ./test-generate-pdf.sh "personal-loan-application.docx" Personal_Loan_Application_form_variables.json
  ./test-generate-pdf.sh my-template.docx my-data.json target/generated-pdfs/my-output.pdf
USAGE
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ $# -lt 2 || $# -gt 3 ]]; then
  usage
  exit 2
fi

TEMPLATE_PATH="$1"
JSON_PATH="$2"
OUTPUT_PATH="${3:-}"

if [[ ! -f "$TEMPLATE_PATH" ]]; then
  echo "Template not found: $TEMPLATE_PATH" >&2
  exit 1
fi

if [[ ! -f "$JSON_PATH" ]]; then
  echo "JSON file not found: $JSON_PATH" >&2
  exit 1
fi

if [[ -z "$OUTPUT_PATH" ]]; then
  base_name="$(basename "$TEMPLATE_PATH")"
  base_name="${base_name%.*}"
  timestamp="$(date +%Y%m%d-%H%M%S)"
  OUTPUT_PATH="$ROOT_DIR/target/generated-pdfs/${base_name}-${timestamp}.pdf"
fi

cd "$ROOT_DIR"

echo "Compiling project..."
mvn -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt

echo "Generating PDF..."
java -cp "target/classes:$(cat target/classpath.txt)" \
  com.bankaudi.baw.document.cli.DocumentGeneratorCli \
  "$TEMPLATE_PATH" \
  "$JSON_PATH" \
  "$OUTPUT_PATH"
