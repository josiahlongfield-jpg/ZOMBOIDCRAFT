#!/bin/sh
# Regenerates the city preview: generator -> voxels -> meshes -> rendered shots.
#   tools/city/run_preview.sh WORK_DIR [shots] [query]
# WORK_DIR/www must hold node_modules (three, playwright) and an http server on :8777.
set -e
W=$1; SHOTS=${2:-street}; Q=${3:-}
J=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}
cd "$(dirname "$0")/../.."
$J/bin/javac -d build/city src/main/java/com/zomboidcraft/world/city/*.java tools/city/CityPreview.java
$J/bin/java -Xmx3g -cp build/city CityPreview "$W/demo" 2>/dev/null
python3 tools/city/paint_textures.py "$W/tex" >/dev/null
python3 tools/city/mesh.py "$W/demo" "$W/tex" "$W/mesh" --near=2,3,60 --near=20,3,-30 --radius 110 | tail -1
node tools/city/shot.js http://localhost:8777 "$W/shot" "$SHOTS" "$Q"
