#!/usr/bin/env bash
# Run this once to download the Gradle wrapper jar and verify JDK 17+
set -e

echo "=== AutoFarm Bootstrap ==="

# Check Java
if ! command -v java &>/dev/null; then
  echo "ERROR: Java not found. Install JDK 17+:"
  echo "  Ubuntu/Debian: sudo apt install openjdk-17-jdk"
  echo "  Or download from: https://adoptium.net"
  exit 1
fi

JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
if [ "$JAVA_VER" -lt 17 ] 2>/dev/null; then
  echo "ERROR: JDK 17+ required. Found: Java $JAVA_VER"
  exit 1
fi
echo "Java OK: $(java -version 2>&1 | head -1)"

# Download Gradle wrapper jar (only needed once)
WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Downloading Gradle wrapper jar..."
  curl -fsSL "https://github.com/gradle/gradle/raw/v8.8.0/gradle/wrapper/gradle-wrapper.jar" \
    -o "$WRAPPER_JAR" || \
  wget -q "https://github.com/gradle/gradle/raw/v8.8.0/gradle/wrapper/gradle-wrapper.jar" \
    -O "$WRAPPER_JAR"
  echo "Wrapper jar downloaded."
fi

# Make gradlew executable
chmod +x gradlew 2>/dev/null || true

echo ""
echo "Bootstrap complete! Now run:"
echo "  ./gradlew :app-ui:run       # Run the app"
echo "  ./gradlew :app-ui:packageDeb  # Build Linux installer"
echo ""
echo "REMINDER: Set up IMAP catch-all email before using OTP steps."
echo "See README.md for instructions."
