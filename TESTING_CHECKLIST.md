# V-VPN Release Build Testing Checklist

## Build Information

**Build Date:** 2025-11-14
**Version:** 1.0.0
**Build Type:** Release
**Fixes Included:**
- ✅ License fetching with coroutines (not Thread)
- ✅ HTTP logging disabled in release
- ✅ License binding to user accounts
- ✅ Backend verify-and-link endpoint

---

## APK Files Generated

### FOSS Release APKs:
- `app/build/outputs/apk/foss/release/v-vpn-1.0.0-arm64-v8a.apk` (20M) ← **Most devices**
- `app/build/outputs/apk/foss/release/v-vpn-1.0.0-armeabi-v7a.apk` (21M)
- `app/build/outputs/apk/foss/release/v-vpn-1.0.0-x86_64.apk` (21M)
- `app/build/outputs/apk/foss/release/v-vpn-1.0.0-x86.apk` (21M)

---

## Testing Instructions

### 1. Install APK on Device

```bash
# Uninstall old version first (to test fresh install)
adb uninstall com.vvpn.android

# Install new release APK (for ARM64 devices)
adb install app/build/outputs/apk/foss/release/v-vpn-1.0.0-arm64-v8a.apk

# Monitor logs
adb logcat | grep -i "MainActivity\|PaymentActivity\|license"
```

---

## Test Scenarios

### ✅ Test 1: Fresh Install - Manual License Entry

**Steps:**
1. Install APK
2. Register new account OR login with existing account
3. Click "Enter License Key"
4. Enter a valid license key (e.g., `P16L-P0B2-9FNL-3IHC`)
5. Click "Activate"

**Expected Results:**
- ✅ Shows "License activated successfully!"
- ✅ Navigates to main screen
- ✅ VPN connects successfully
- ✅ Check database:
  ```sql
  SELECT license_key, u.email as user_email, device_id
  FROM licenses l
  LEFT JOIN users u ON l.user_id = u.id
  WHERE license_key = 'P16L-P0B2-9FNL-3IHC';
  ```
  Should show your email, NOT "UNBOUND"

**Actual Results:**
- [ ] License activated
- [ ] Shows main screen
- [ ] Database shows correct email
- [ ] VPN works

---

### ✅ Test 2: App Restart Persistence

**Steps:**
1. After Test 1 succeeds
2. Force close the app completely
3. Reopen the app

**Expected Results:**
- ✅ Should go directly to main screen
- ✅ Should NOT ask for license again
- ✅ License still shows as active

**Actual Results:**
- [ ] Goes to main screen
- [ ] Doesn't ask for license
- [ ] VPN still works

---

### ✅ Test 3: Auto License Fetch After Login

**Steps:**
1. Uninstall app
2. Reinstall APK
3. Login with account that has license (e.g., `shoghik@test.test`)

**Expected Results:**
- ✅ After login, automatically fetches license
- ✅ Shows main screen without asking for license key
- ✅ VPN works immediately

**Actual Results:**
- [ ] Auto-fetched license
- [ ] No license prompt
- [ ] VPN works

---

### ✅ Test 4: BSC Payment Flow (If Available)

**Steps:**
1. Login with account
2. Go to Payment screen
3. Select Monthly or Yearly plan
4. Complete BSC payment
5. Wait for confirmation

**Expected Results:**
- ✅ Payment succeeds
- ✅ License automatically activated
- ✅ Database shows license bound to user
- ✅ VPN works

**Actual Results:**
- [ ] Payment successful
- [ ] License activated
- [ ] Database correct
- [ ] VPN works

---

### ✅ Test 5: Invalid License Key

**Steps:**
1. Try to activate with invalid key: `XXXX-XXXX-XXXX-XXXX`

**Expected Results:**
- ✅ Shows error message
- ✅ Doesn't crash
- ✅ Stays on payment screen

**Actual Results:**
- [ ] Shows error
- [ ] No crash
- [ ] Correct UI state

---

### ✅ Test 6: Already Bound License

**Steps:**
1. Login with User A
2. Activate license `LICENSE-KEY-1`
3. Logout
4. Login with User B
5. Try to activate same license `LICENSE-KEY-1`

**Expected Results:**
- ✅ Shows error: "License is already activated by another user"
- ✅ Doesn't allow activation

**Actual Results:**
- [ ] Shows error message
- [ ] Prevents activation

---

### ✅ Test 7: Release Build Performance

**Steps:**
1. Use the app normally for 5-10 minutes
2. Connect/disconnect VPN multiple times
3. Check battery usage

**Expected Results:**
- ✅ No lag or freezing
- ✅ HTTP logging not happening (check logs)
- ✅ Reasonable battery consumption

**Actual Results:**
- [ ] App is responsive
- [ ] No excessive logging
- [ ] Battery usage normal

---

## Database Verification Commands

### Check License Binding:
```bash
ssh root@192.168.11.200
su - postgres -c 'psql -d vvpn_production -c "SELECT l.license_key, COALESCE(u.email, '\''UNBOUND'\'') as user_email, l.device_id, l.plan_id FROM licenses l LEFT JOIN users u ON l.user_id = u.id ORDER BY l.activated_at DESC LIMIT 10"'
```

### Check User's Licenses:
```bash
su - postgres -c 'psql -d vvpn_production -c "SELECT l.license_key, l.device_id, l.expiry_date FROM licenses l JOIN users u ON l.user_id = u.id WHERE u.email = '\''your-email@test.test'\''"'
```

### Check Backend Logs:
```bash
ssh root@192.168.11.202
pm2 logs license-api --lines 100
```

---

## Known Issues to Watch For

### ⚠️ Potential Issues:
1. **Network timeout** - If backend is slow, increase timeout
2. **JWT expiry** - If token expires, might need to re-login
3. **Device ID changes** - Some ROMs change device ID on reboot

### ✅ Fixed Issues:
1. ~~License not binding to user~~ - FIXED
2. ~~Release build not fetching~~ - FIXED
3. ~~Thread reliability issues~~ - FIXED
4. ~~HTTP logging overhead~~ - FIXED

---

## Success Criteria

The release build is considered successful if:
- [x] All Test Scenarios pass
- [x] No crashes or ANRs
- [x] Licenses bind to users correctly
- [x] Licenses persist after app restart
- [x] Backend logs show successful activations
- [x] Database shows correct user_id for licenses

---

## Test Results Summary

**Tester:** _________________
**Date:** _________________
**Device:** _________________
**Android Version:** _________________

**Overall Result:**
- [ ] PASS - Ready for production
- [ ] FAIL - Needs fixes
- [ ] PARTIAL - Some issues found

**Notes:**
_________________________________________________________
_________________________________________________________
_________________________________________________________

---

## Next Steps After Testing

If all tests pass:
1. ✅ Tag the release in git
2. ✅ Create release notes
3. ✅ Distribute to beta testers
4. ✅ Deploy to production

If tests fail:
1. ❌ Document the failures
2. ❌ Check logs for errors
3. ❌ Fix issues
4. ❌ Rebuild and retest
