#!/bin/bash

echo "==========================================="
echo "V-VPN Auto-Update Verification Script"
echo "==========================================="
echo ""

SUCCESS_COUNT=0
TOTAL_CHECKS=0

check() {
    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
    if [ $1 -eq 0 ]; then
        echo "  ✅ $2"
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
    else
        echo "  ❌ $2"
    fi
}

# Check 1: Backend API reachable
echo "1. Checking Backend API..."
curl -s -f -m 5 http://192.168.11.202:3000/api/app/version > /dev/null 2>&1
check $? "Backend API is reachable"

# Check 2: API returns valid JSON
echo ""
echo "2. Checking API Response..."
RESPONSE=$(curl -s -m 5 http://192.168.11.202:3000/api/app/version)
echo "$RESPONSE" | python3 -c "import json,sys; json.load(sys.stdin)" > /dev/null 2>&1
check $? "API returns valid JSON"

# Check 3: API contains version info
echo ""
echo "3. Checking Version Information..."
VERSION_CODE=$(echo "$RESPONSE" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('version',{}).get('versionCode',''))" 2>/dev/null)
[ "$VERSION_CODE" = "100" ]
check $? "Version code is correct (100)"

# Check 4: APK download URL is accessible
echo ""
echo "4. Checking APK Download..."
APK_URL=$(echo "$RESPONSE" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('version',{}).get('apkUrl',''))" 2>/dev/null)
curl -s -f -I -m 10 "$APK_URL" > /dev/null 2>&1
check $? "APK download URL is accessible"

# Check 5: APK file size matches
echo ""
echo "5. Checking APK Size..."
APK_SIZE=$(echo "$RESPONSE" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('version',{}).get('apkSize',''))" 2>/dev/null)
[ "$APK_SIZE" = "42965601" ]
check $? "APK size matches (41 MB)"

# Check 6: APK hash is correct
echo ""
echo "6. Checking APK Hash..."
APK_HASH=$(echo "$RESPONSE" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('version',{}).get('apkHash',''))" 2>/dev/null)
[ "$APK_HASH" = "58dacc8d76a0a9ce65e92153bf4663976292b1da8a07e1a729daa502be4be606" ]
check $? "APK hash is correct"

# Check 7: Android components exist
echo ""
echo "7. Checking Android Components..."
[ -f "app/src/main/java/com/vvpn/android/network/UpdateChecker.kt" ]
check $? "UpdateChecker.kt exists"

[ -f "app/src/main/java/com/vvpn/android/network/UpdateManager.kt" ]
check $? "UpdateManager.kt exists"

[ -f "app/src/main/java/com/vvpn/android/network/UpdateNotificationManager.kt" ]
check $? "UpdateNotificationManager.kt exists"

[ -f "app/src/main/java/com/vvpn/android/network/UpdateActionReceiver.kt" ]
check $? "UpdateActionReceiver.kt exists"

# Check 8: APK file exists
echo ""
echo "8. Checking Build Output..."
[ -f "app/build/outputs/apk/foss/release/v-vpn-1.0.0.apk" ]
check $? "Release APK exists"

# Check 9: FileProvider configuration
echo ""
echo "9. Checking AndroidManifest Configuration..."
grep -q 'android:name="androidx.core.content.FileProvider"' app/src/main/AndroidManifest.xml
check $? "FileProvider is configured"

grep -q 'android:name="com.vvpn.android.network.UpdateActionReceiver"' app/src/main/AndroidManifest.xml
check $? "UpdateActionReceiver is registered"

# Summary
echo ""
echo "==========================================="
echo "Verification Summary"
echo "==========================================="
echo ""
echo "Passed: $SUCCESS_COUNT / $TOTAL_CHECKS checks"
echo ""

if [ $SUCCESS_COUNT -eq $TOTAL_CHECKS ]; then
    echo "✅ All checks passed! Auto-update system is ready."
    echo ""
    echo "Next steps:"
    echo "  1. Install APK on Android device:"
    echo "     adb install app/build/outputs/apk/foss/release/v-vpn-1.0.0.apk"
    echo ""
    echo "  2. Test automatic update check (launches with app)"
    echo ""
    echo "  3. Test manual update check:"
    echo "     Open app → Menu → About → Check for Updates"
    echo ""
    echo "  4. Simulate update available:"
    echo "     - SSH to 192.168.11.202"
    echo "     - Edit /opt/license-api/src/routes/app.js"
    echo "     - Change versionCode: 100 → 101"
    echo "     - Restart: pm2 restart license-api"
    echo "     - Launch app → Update notification appears"
    echo ""
    exit 0
else
    echo "❌ Some checks failed. Please review the issues above."
    echo ""
    exit 1
fi
