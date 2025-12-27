#!/bin/bash

# MySQL JDBC Driver - check multiple possible locations
DRIVER_JAR=""

# Check common locations
if [ -f "mysql-connector-java-8.0.33.jar" ]; then
    DRIVER_JAR="mysql-connector-java-8.0.33.jar"
elif [ -f "mysql-connector-java-8.2.0.jar" ]; then
    DRIVER_JAR="mysql-connector-java-8.2.0.jar"
elif [ -f "mysql-connector-j-8.2.0.jar" ]; then
    DRIVER_JAR="mysql-connector-j-8.2.0.jar"
elif [ -f ~/.m2/repository/com/mysql/mysql-connector-j/8.2.0/mysql-connector-j-8.2.0.jar ]; then
    DRIVER_JAR=~/.m2/repository/com/mysql/mysql-connector-j/8.2.0/mysql-connector-j-8.2.0.jar
elif [ -f ~/.m2/repository/com/mysql/mysql-connector-java/*/mysql-connector-java-*.jar ]; then
    DRIVER_JAR=$(ls ~/.m2/repository/com/mysql/mysql-connector-java/*/mysql-connector-java-*.jar | head -1)
fi

# If still not found, try to find any mysql connector jar
if [ -z "$DRIVER_JAR" ]; then
    FOUND_JAR=$(find ~/.m2/repository -name "mysql-connector*.jar" 2>/dev/null | head -1)
    if [ -n "$FOUND_JAR" ]; then
        DRIVER_JAR="$FOUND_JAR"
    fi
fi

# Check if driver exists
if [ -z "$DRIVER_JAR" ] || [ ! -f "$DRIVER_JAR" ]; then
    echo "❌ MySQL JDBC Driver not found!"
    echo ""
    echo "Tried to find driver in:"
    echo "  - Project root (mysql-connector-java-*.jar)"
    echo "  - Maven repository (~/.m2/repository/com/mysql/)"
    echo ""
    echo "To fix:"
    echo "1. Download from: https://dev.mysql.com/downloads/connector/j/"
    echo "2. Or if you have Maven, it will be auto-downloaded"
    echo "3. Place mysql-connector-java-*.jar in project root"
    exit 1
fi

echo "Using MySQL driver: $DRIVER_JAR"

# Find Lombok
LOMBOK_JAR=""
if [ -f "lombok.jar" ]; then
    LOMBOK_JAR="lombok.jar"
elif [ -f ~/.m2/repository/org/projectlombok/lombok/*/lombok-*.jar ]; then
    LOMBOK_JAR=$(ls ~/.m2/repository/org/projectlombok/lombok/*/lombok-*.jar | head -1)
fi

if [ -z "$LOMBOK_JAR" ] || [ ! -f "$LOMBOK_JAR" ]; then
    echo "❌ Lombok not found!"
    echo "Copying from Maven repository..."
    mkdir -p ~/.m2/repository/org/projectlombok/lombok/1.18.30/ 2>/dev/null
    if [ -f ~/.m2/repository/org/projectlombok/lombok/1.18.30/lombok-1.18.30.jar ]; then
        cp ~/.m2/repository/org/projectlombok/lombok/1.18.30/lombok-1.18.30.jar ./lombok.jar
        LOMBOK_JAR="lombok.jar"
        echo "✓ Lombok copied to project root"
    else
        echo "Please download Lombok from: https://projectlombok.org/download"
        exit 1
    fi
fi

# Build classpath
CLASSPATH=".:$DRIVER_JAR:$LOMBOK_JAR"

# Compile if needed - check if any source file is newer than class files
NEED_COMPILE=false
if [ ! -f "Application.class" ] || \
   [ "Application.java" -nt "Application.class" ] || \
   [ "config/DatabaseConfiguration.java" -nt "config/DatabaseConfiguration.class" ] || \
   [ "strategy/HuggingFaceStrategy.java" -nt "strategy/HuggingFaceStrategy.class" ] || \
   [ "api/ApiServer.java" -nt "api/ApiServer.class" ] || \
   [ "service/ContentSummarizerService.java" -nt "service/ContentSummarizerService.class" ]; then
    NEED_COMPILE=true
fi

if [ "$NEED_COMPILE" = true ]; then
    echo "Compiling..."
    javac -cp "$CLASSPATH" -processor lombok.launch.AnnotationProcessorHider\$AnnotationProcessor \
        config/*.java \
        enums/*.java \
        models/*.java \
        strategy/*.java \
        dao/*.java \
        worker/*.java \
        service/*.java \
        api/*.java \
        util/*.java \
        Application.java
    if [ $? -ne 0 ]; then
        echo "❌ Compilation failed!"
        exit 1
    fi
    echo "✓ Compilation successful!"
fi

# Run the application
echo "Running application..."
java -cp ".:$DRIVER_JAR:$LOMBOK_JAR:config:dao:worker:strategy:enums:models:service:api:util" Application

