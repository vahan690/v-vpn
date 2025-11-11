#!/bin/bash

set -e

BACKUP_DIR="app/src/main/java_ROLLBACK_$(date +%Y%m%d_%H%M%S)"
NEW_PACKAGE="io.vvpn.android"
OLD_PACKAGE="io.nekohasekai.sagernet"

echo "🔄 V-VPN Package Refactoring Script v3 (No Kotlin Syntax Check)"
echo "📁 Creating rollback backup: $BACKUP_DIR"

cp -r app/src/main/java "$BACKUP_DIR"

# Simple file update without syntax checking
safe_update_file() {
    local file="$1"
    [[ ! -f "$file" ]] && return 0
    
    # Update package declaration
    sed -i "s|^package $OLD_PACKAGE|package $NEW_PACKAGE|g" "$file"
    
    # Update imports
    sed -i "s|^import $OLD_PACKAGE|import $NEW_PACKAGE|g" "$file"
    
    # Update other references
    sed -i "s|$OLD_PACKAGE\.|$NEW_PACKAGE.|g" "$file"
    
    echo "✅ Updated: $file"
}

rollback() {
    echo "❌ Error occurred, rolling back..."
    rm -rf app/src/main/java
    cp -r "$BACKUP_DIR" app/src/main/java
    echo "🔄 Rollback completed"
    exit 1
}

trap rollback ERR

# Update build configuration
echo "📝 Step 1: Updating build configuration"
sed -i 's|namespace = "io.nekohasekai.sagernet"|namespace = "io.vvpn.android"|g' app/build.gradle.kts
sed -i 's|applicationId = "io.nekohasekai.sagernet"|applicationId = "io.vvpn.android"|g' app/build.gradle.kts

# Update AndroidManifest.xml
echo "📝 Step 2: Updating AndroidManifest.xml"
sed -i "s|$OLD_PACKAGE|$NEW_PACKAGE|g" app/src/main/AndroidManifest.xml

# Create new package structure
echo "📁 Step 3: Creating new package structure"
mkdir -p "app/src/main/java/io/vvpn/android"

# Update all source files
echo "📝 Step 4: Updating source files"
find app/src/main/java/io/nekohasekai/sagernet -name "*.kt" -o -name "*.java" | while read file; do
    safe_update_file "$file"
done

# Move files to new structure
echo "📁 Step 5: Moving files to new package structure"
cp -r app/src/main/java/io/nekohasekai/sagernet/* app/src/main/java/io/vvpn/android/

# Update resource files
echo "📝 Step 6: Updating resource files"
find app/src/main/res -name "*.xml" -exec sed -i "s|$OLD_PACKAGE|$NEW_PACKAGE|g" {} \; 2>/dev/null || true

echo "✅ Refactoring completed!"
echo "📁 Rollback backup: $BACKUP_DIR"
echo "🚀 Test with: make apk_debug"
