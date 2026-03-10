#!/bin/sh
#
# Gradle start up script for UN*X
#
APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
die () {
    echo
    echo "ERROR: $*"
    echo
    exit 1
} >&2
warn () {
    echo "$*"
} >&2
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
guess_runnable_prefix () {
    JAVACMD="$JAVA_HOME/bin/java"
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is not set correctly."
    fi
    echo "$JAVACMD"
}
APP_HOME=`pwd -P`
JAVACMD=`guess_runnable_prefix`
exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
    "-Dorg.gradle.appname=$APP_BASE_NAME" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
