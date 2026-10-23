#!/usr/bin/env bash
# ---------------------------------------------------------------------------
#  CivicEye — re-render the nine UML diagrams from their PlantUML sources.
#
#  Requirements: a JDK (17+) and plantuml.jar in this folder or on $PATH.
#      curl -L -o plantuml.jar \
#        https://github.com/plantuml/plantuml/releases/latest/download/plantuml.jar
#
#  Usage:
#      ./render.sh          # writes NN_name.png (scale 2) and NN_name.svg
#
#  PlantUML names its output after the identifier inside @startuml, so the
#  script renames every artefact back to the name of its .puml source.
# ---------------------------------------------------------------------------
set -euo pipefail
cd "$(dirname "$0")"

JAR="${PLANTUML_JAR:-}"
if [ -z "$JAR" ]; then
  if [ -f "plantuml.jar" ]; then
    JAR="plantuml.jar"
  elif command -v plantuml >/dev/null 2>&1; then
    JAR="plantuml"
  else
    echo "plantuml.jar not found. Download it or set PLANTUML_JAR=/path/to/plantuml.jar" >&2
    exit 1
  fi
fi

OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

echo "Rendering with $JAR ..."
java -jar "$JAR" -S2 -tpng -o "$OUT" ./*.puml
java -jar "$JAR" -tsvg  -o "$OUT" ./*.puml

for src in ./*.puml; do
  base="$(basename "$src" .puml)"
  name="$(sed -n 's/^@startuml[[:space:]]*//p' "$src" | head -1)"
  for ext in png svg; do
    if [ -n "$name" ] && [ -f "$OUT/$name.$ext" ]; then
      mv -f "$OUT/$name.$ext" "$base.$ext"
    fi
  done
done

echo "Done. Artifacts:"
ls -1 ./*.png ./*.svg
