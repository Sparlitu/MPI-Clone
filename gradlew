#!/bin/sh

# Gradle startup script for Unix

# Attempt to set APP_HOME
# Resolve links: $0 may be a link
PRG="$0"
# Need this for relative symlinks.
while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`"/$link"
    fi
done
SAVED="`pwd`"
cd "`dirname \"$PRG\"`/" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        echo "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
        exit 1
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || {
        echo "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
        exit 1
    }
fi

DEFAULT_JVM_OPTS=""

# Collect all arguments for the java command
if $JAVACMD --add-opens java.base/java.lang=ALL-UNNAMED -version >/dev/null 2>&1; then
    DEFAULT_JVM_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED $DEFAULT_JVM_OPTS"
fi

# Run Gradle
exec "$JAVACMD" $DEFAULT_JVM_OPTS -Dorg.gradle.appname="Gradle" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
