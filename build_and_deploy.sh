#!/bin/bash

# V-VPN Build and Deploy Script
# Builds APK and automatically updates backend with new hash/size

set -e

# Configuration
APK_OUTPUT_DIR="app/build/outputs/apk/foss/release"
APK_NAME="v-vpn-1.0.0.apk"
BACKEND_HOST="192.168.11.202"
BACKEND_USER="root"
SSH_KEY="$HOME/.ssh/id_rsa_license"
API_CONFIG_PATH="/opt/license-api/src/routes/app.js"

# Extract version from vvpn.properties
VERSION_CODE=$(grep '^VERSION_CODE=' vvpn.properties | cut -d= -f2)
VERSION_NAME=$(grep '^VERSION_NAME=' vvpn.properties | cut -d= -f2)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "==========================================="
echo "V-VPN Build and Deploy Script"
echo "==========================================="
echo ""

# Step 1: Build APK
echo -e "${YELLOW}Step 1: Building APK...${NC}"
export JAVA_HOME=/usr/lib/jvm/jdk-21.0.8+9
export BUILD_PLUGIN=none
./gradlew :app:assembleFossRelease

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Build failed!${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Build successful${NC}"
echo ""

# Step 2: Find and verify APK
APK_PATH="$APK_OUTPUT_DIR/$APK_NAME"
if [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}❌ APK not found at: $APK_PATH${NC}"
    exit 1
fi

echo -e "${YELLOW}Step 2: Calculating APK details...${NC}"

# Calculate SHA-256 hash
APK_HASH=$(sha256sum "$APK_PATH" | awk '{print $1}')
echo "  Hash: $APK_HASH"

# Get file size
APK_SIZE=$(stat -c%s "$APK_PATH")
echo "  Size: $APK_SIZE bytes ($(numfmt --to=iec-i --suffix=B $APK_SIZE))"

echo -e "${GREEN}✅ APK details calculated${NC}"
echo ""

# Step 3: Update backend
echo -e "${YELLOW}Step 3: Updating backend API...${NC}"
echo "  Version: $VERSION_NAME (code: $VERSION_CODE)"

SSH_CMD="ssh -i $SSH_KEY -o PreferredAuthentications=publickey -o StrictHostKeyChecking=no $BACKEND_USER@$BACKEND_HOST"
export SSH_AUTH_SOCK=""

# Update version, hash and size in app.js
# Note: Using patterns that match the definition lines (ending with comma) to avoid corrupting APP_VERSION references
$SSH_CMD "
    # Update versionCode (match line ending with comma)
    sed -i \"s/versionCode: [0-9]*,/versionCode: $VERSION_CODE,/\" $API_CONFIG_PATH

    # Update versionName
    sed -i \"s/versionName: '[^']*',/versionName: '$VERSION_NAME',/\" $API_CONFIG_PATH

    # Update apkHash
    sed -i \"s/apkHash: '[a-f0-9]*',/apkHash: '$APK_HASH',/\" $API_CONFIG_PATH

    # Update apkSize (match line ending with comma to avoid APP_VERSION.apkSize)
    sed -i \"s/apkSize: [0-9]*,/apkSize: $APK_SIZE,/\" $API_CONFIG_PATH

    echo 'Configuration updated'
"

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Failed to update backend configuration${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Backend configuration updated${NC}"
echo ""

# Step 4: Restart API service
echo -e "${YELLOW}Step 4: Restarting API service...${NC}"

$SSH_CMD "pm2 restart license-api && sleep 2 && pm2 status license-api | grep license-api"

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Failed to restart API service${NC}"
    exit 1
fi

echo -e "${GREEN}✅ API service restarted${NC}"
echo ""

# Step 5: Verify update
echo -e "${YELLOW}Step 5: Verifying backend update...${NC}"

VERIFY_HASH=$($SSH_CMD "grep 'apkHash' $API_CONFIG_PATH | grep -o \"'[a-f0-9]*'\" | tr -d \"'\"")
VERIFY_SIZE=$($SSH_CMD "grep 'apkSize' $API_CONFIG_PATH | grep -o '[0-9]*'")

echo "  Backend Hash: $VERIFY_HASH"
echo "  Backend Size: $VERIFY_SIZE"

if [ "$VERIFY_HASH" = "$APK_HASH" ] && [ "$VERIFY_SIZE" = "$APK_SIZE" ]; then
    echo -e "${GREEN}✅ Verification passed${NC}"
else
    echo -e "${RED}❌ Verification failed - values don't match!${NC}"
    exit 1
fi

echo ""
echo "==========================================="
echo -e "${GREEN}Deployment Complete!${NC}"
echo "==========================================="
echo ""
echo "APK Details:"
echo "  Path: $APK_PATH"
echo "  Hash: $APK_HASH"
echo "  Size: $APK_SIZE bytes"
echo ""
echo "Next steps:"
echo "  1. Upload APK to web server if needed:"
echo "     scp $APK_PATH root@192.168.11.103:/var/www/html/vvpn.space/downloads/v-vpn.apk"
echo ""
echo "  2. Test update on Android device"
echo ""
