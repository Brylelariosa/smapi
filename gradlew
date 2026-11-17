#!/bin/sh
#
# Minimal Gradle wrapper launcher. The wrapper jar (gradle/wrapper/gradle-wrapper.jar) is a binary that is not shipped in
# this zip; CI fetches it (or run `gradle wrapper --gradle-version 8.9` once to regenerate the full standard scripts).
# The Gradle version to use is read from gradle/wrapper/gradle-wrapper.properties by the jar itself.
#
APP_HOME=$(cd "$(dirname "$0")" && pwd -P) || exit 1
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD=java
fi

if [ ! -f "$CLASSPATH" ]; then
    echo "gradlew: $CLASSPATH is missing. Download the gradle-wrapper.jar that matches your Gradle version," >&2
    echo "or run 'gradle wrapper --gradle-version 8.9' once." >&2
    exit 1
fi

exec "$JAVACMD" $JAVA_OPTS $GRADLE_OPTS "-Dorg.gradle.appname=gradlew" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
