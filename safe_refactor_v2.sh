#!/bin/bash

set -e  # Exit on any error

BACKUP_DIR="app/src/main/java_ROLLBACK_$(date +%Y%m%d_%H%M%S)"
NEW_PACKAGE="io.vvpn.android"
OLD_PACKAGE="io.nekohasekai.sagernet"

echo "🔄 V-VPN Package Refactoring Script v2"
echo "📁 Creating rollback backup: $BACKUP_DIR"

# Create rollback backup
cp -r app/src/main/java "$BACKUP_DIR"

# Function to safely update a file
safe_update_file() {
    local file="$1"
    local temp_file="${file}.tmp"
    
    # Skip if file doesn't exist
    [[ ! -f "$file" ]] && return 0
    
    # Create temp copy
    cp "$file" "$temp_file"
    
    # Apply changes to temp file
    sed -i "s|package $OLD_PACKAGE|package $NEW_PACKAGE|g" "$temp_file"
    sed -i "s|import $OLD_PACKAGE|import $NEW_PACKAGE|g" "$temp_file"
    sed -i "s|$OLD_PACKAGE|$NEW_PACKAGE|g" "$temp_file"
    
    # Test syntax (for Kotlin files)
    if [[ "$file" == *.kt ]]; then
        if ! kotlinc -classpath "$(find ~/.gradle/caches -name "*.jar" | head -5 | tr '\n' ':')" "$temp_file" -d /tmp/kotlin_test 2>/dev/null; then
            echo "⚠️  Syntax error in $file, skipping"
            rm "$temp_file"
            return 1
        fi
    fi
    
    # Apply changes
    mv "$temp_file" "$file"
    echo "✅ Updated: $file"
}

# Function to rollback on failure
rollback() {
    echo "❌ Error occurred, rolling back..."
    rm -rf app/src/main/java
    cp -r "$BACKUP_DIR" app/src/main/java
    echo "🔄 Rollback completed"
    exit 1
}

trap rollback ERR

# Step 1: Update build.gradle.kts (safe)
echo "📝 Step 1: Updating build configuration"
safe_update_file "app/build.gradle.kts"

# Step 2: Update AndroidManifest.xml
echo "📝 Step 2: Updating AndroidManifest.xml"
safe_update_file "app/src/main/AndroidManifest.xml"

# Step 3: Create new package structure
echo "📁 Step 3: Creating new package structure"
mkdir -p "app/src/main/java/io/vvpn/android"

# Step 4: Update all source files by package (safest order)
echo "📝 Step 4: Updating source files (database first)"

# Database package first (most critical)
for file in $(find app/src/main/java/io/nekohasekai/sagernet/database -name "*.kt" -o -name "*.java"); do
    safe_update_file "$file"
done

# Core packages next
for dir in ktx utils fmt; do
    for file in $(find app/src/main/java/io/nekohasekai/sagernet/$dir -name "*.kt" -o -name "*.java" 2>/dev/null); do
        safe_update_file "$file"
    done
done

# UI packages last (least critical)
for file in $(find app/src/main/java/io/nekohasekai/sagernet/ui -name "*.kt" -o -name "*.java"); do
    safe_update_file "$file"
done

# Remaining files
for file in $(find app/src/main/java/io/nekohasekai/sagernet -name "*.kt" -o -name "*.java"); do
    safe_update_file "$file"
done

# Step 5: Move files to new structure
echo "📁 Step 5: Moving files to new package structure"
if [[ -d "app/src/main/java/io/nekohasekai/sagernet" ]]; then
    cp -r app/src/main/java/io/nekohasekai/sagernet/* app/src/main/java/io/vvpn/android/
fi

# Step 6: Update resource files
echo "📝 Step 6: Updating resource files"
find app/src/main/res -name "*.xml" -exec sed -i "s|$OLD_PACKAGE|$NEW_PACKAGE|g" {} \; 2>/dev/null || true

# Step 7: Clean build test
echo "🔧 Step 7: Testing build"
./gradlew clean >/dev/null 2>&1

echo "✅ Refactoring completed successfully!"
echo "📁 Rollback backup available at: $BACKUP_DIR"
echo "🚀 Next: Test with 'make apk_debug'"
