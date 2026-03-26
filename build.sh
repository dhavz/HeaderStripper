#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="$ROOT_DIR/src/main/java"
BUILD_DIR="$ROOT_DIR/build"
CLASSES_DIR="$BUILD_DIR/classes"
DIST_DIR="$ROOT_DIR/dist"
DEFAULT_API_JAR="$ROOT_DIR/lib/burp-extender-api.jar"
API_JAR="${BURP_API_JAR:-$DEFAULT_API_JAR}"

if [[ ! -f "$API_JAR" ]]; then
  echo "Burp Extender API JAR not found."
  echo "Provide BURP_API_JAR=/path/to/burp-extender-api.jar or place the file at lib/burp-extender-api.jar"
  exit 1
fi

rm -rf "$BUILD_DIR" "$DIST_DIR"
mkdir -p "$CLASSES_DIR" "$DIST_DIR"
find "$SRC_DIR" -name '*.java' | sort > "$BUILD_DIR/sources.txt"

javac   --release 8   -cp "$API_JAR"   -d "$CLASSES_DIR"   @"$BUILD_DIR/sources.txt"

jar cf "$DIST_DIR/header-stripper-burp-extension.jar" -C "$CLASSES_DIR" .

echo "Built:"
echo "  $DIST_DIR/header-stripper-burp-extension.jar"
