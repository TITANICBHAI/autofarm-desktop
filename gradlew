#!/bin/sh
#
# Gradle start up script for UN*X
#
set -e
APP_HOME="$(cd "$(dirname "$0")" && pwd)"
APP_NAME="Gradle"
APP_BASE_NAME="$(basename "$0")"

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD=maximum

warn() {
    echo "$*"
} >&2

die() {
    echo
    echo "$*"
    echo
    exit 1
} >&2

# OS-specific support
cygwin=false
msys=false
darwin=false
nonstop=false
case "$(uname)" in
    CYGWIN* ) cygwin=true ;;
    Darwin* ) darwin=true ;;
    MSYS* | MINGW* ) msys=true ;;
    NONSTOP* ) nonstop=true ;;
esac

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Determine JAVA_EXE
if [ -n "$JAVA_HOME" ]; then
    JAVA_EXE="$JAVA_HOME/bin/java"
else
    JAVA_EXE="java"
fi

if ! command -v "$JAVA_EXE" >/dev/null 2>&1; then
    die "ERROR: JAVA_HOME is not set and no 'java' command could be found.
Please set the JAVA_HOME variable to the location of your Java installation."
fi

exec "$JAVA_EXE" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
