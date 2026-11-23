#!/bin/bash
# Builds the Mac installer: target/installer/CUE 147-<version>.dmg
# Needs JDK 17 or newer (jpackage is part of the JDK). Run on a Mac.
set -e
cd "$(dirname "$0")"
VERSION=$(sed -n 's/.*<version>\(.*\)<\/version>.*/\1/p' pom.xml | head -1)

echo "Building CUE 147 $VERSION ..."
./mvnw -q -B -DskipTests package

# everything the app needs in one folder (the jar plus the libraries it uses)
rm -rf target/installer-input target/installer
mkdir -p target/installer-input target/installer
cp "target/cue147-$VERSION.jar" target/installer-input/
cp -R target/lib target/installer-input/lib

jpackage \
  --name "CUE 147" \
  --app-version "$VERSION" \
  --input target/installer-input \
  --main-jar "cue147-$VERSION.jar" \
  --main-class app.Main \
  --icon packaging/cue147.icns \
  --type dmg \
  --dest target/installer \
  --vendor "CUE 147" \
  --copyright "CUE 147" \
  --description "Snooker and pool club management" \
  --mac-package-name "CUE 147" \
  --java-options "-Xmx512m" \
  --java-options "--enable-native-access=ALL-UNNAMED" \
  --java-options "-Dapple.awt.application.appearance=system"

echo
echo "Done:"
ls -la target/installer
echo
echo "Open the .dmg and drag CUE 147 to Applications. Start it like any other Mac app."
