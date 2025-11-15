# V-VPN Backend API Requirements

This document outlines the API endpoints that the V-VPN Android app expects from the backend server running at `https://api.vvpn.space`.

## Issue Summary

- **Debug Build**: License fetching works ✓
- **Release Build**:
  - Auto license fetching after login: NOT WORKING ✗
  - Manual license entry: Shows "Route not found" error ✗

## Root Cause

The Android app was using raw `Thread` instead of coroutines for network requests, causing reliability issues in release builds. This has been **FIXED** in the latest commit.

However, there's also a **backend issue** - the route for manual license verification may not exist or is returning "Route not found".

---

## Required API Endpoints

### 1. License Verification (Manual Entry)

**Endpoint:** `GET /api/license/verify/:deviceId/:licenseKey`

**Full URL:** `https://api.vvpn.space/api/license/verify/{deviceId}/{licenseKey}`

**Purpose:** Verify a license key when user enters it manually

**Request:**
- Method: GET
- Path Parameters:
  - `deviceId`: Android device ID (from Settings.Secure.ANDROID_ID)
  - `licenseKey`: License key in format XXXX-XXXX-XXXX-XXXX
- Headers: None required

**Success Response (200):**
```json
{
  "success": true,
  "isValid": true,
  "message": "License is valid",
  "license": {
    "licenseKey": "ABCD-EFGH-IJKL-MNOP",
    "planId": "monthly",
    "expiryDate": "2025-12-31T23:59:59.999Z"
  }
}
```

**Error Response (400/404):**
```json
{
  "success": false,
  "isValid": false,
  "error": "Invalid license key"
}
```

**Android Code Location:** `app/src/main/java/com/vvpn/android/payment/PaymentManager.kt:157`

---

### 2. Fetch Licenses by Device ID

**Endpoint:** `GET /api/license/device/:deviceId`

**Full URL:** `https://api.vvpn.space/api/license/device/{deviceId}`

**Purpose:** Fetch all licenses associated with a device after user login

**Request:**
- Method: GET
- Path Parameters:
  - `deviceId`: Android device ID
- Headers: None required (but could add Authorization if needed)

**Success Response (200):**
```json
{
  "success": true,
  "licenses": [
    {
      "license_key": "ABCD-EFGH-IJKL-MNOP",
      "user_email": "user@example.com",
      "plan_id": "monthly",
      "expiry_date": "2025-12-31T23:59:59.999Z"
    }
  ]
}
```

**No Licenses Found (200):**
```json
{
  "success": true,
  "licenses": []
}
```

**Android Code Location:** `app/src/main/java/com/vvpn/android/ui/MainActivity.kt:860`

---

### 3. Verify and Link License (Optional)

**Endpoint:** `POST /api/license/verify-and-link`

**Full URL:** `https://api.vvpn.space/api/license/verify-and-link`

**Purpose:** Verify a license and link it to a user account

**Request:**
- Method: POST
- Headers:
  - `Content-Type: application/json`
  - `Authorization: Bearer {jwt_token}`
- Body:
```json
{
  "licenseKey": "ABCD-EFGH-IJKL-MNOP",
  "deviceId": "device123456"
}
```

**Success Response (200):**
```json
{
  "success": true,
  "isValid": true,
  "message": "License verified and linked",
  "license": {
    "licenseKey": "ABCD-EFGH-IJKL-MNOP",
    "planId": "monthly",
    "expiryDate": "2025-12-31T23:59:59.999Z"
  }
}
```

**Android Code Location:** `app/src/main/java/com/vvpn/android/payment/PaymentManager.kt:208`

---

### 4. Authentication Endpoints

**Login:** `POST /api/auth/login`
**Register:** `POST /api/auth/register`

These endpoints are already working ✓

---

## Database Schema Requirements

Your PostgreSQL database should have a `licenses` table with at least:

```sql
CREATE TABLE licenses (
    id SERIAL PRIMARY KEY,
    license_key VARCHAR(255) UNIQUE NOT NULL,
    user_email VARCHAR(255) NOT NULL,
    device_id VARCHAR(255),
    plan_id VARCHAR(50) NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Index for fast lookups
CREATE INDEX idx_license_device ON licenses(device_id);
CREATE INDEX idx_license_key ON licenses(license_key);
CREATE INDEX idx_license_email ON licenses(user_email);
```

---

## Troubleshooting Steps

### Step 1: Verify Backend Routes Exist

SSH into your server:
```bash
ssh root@your-server-ip
cd /path/to/license-api-node
cat app.js  # or your main server file
```

Look for routes like:
```javascript
app.get('/api/license/verify/:deviceId/:licenseKey', ...)
app.get('/api/license/device/:deviceId', ...)
```

### Step 2: Test Endpoints Manually

```bash
# Test manual license verification
curl https://api.vvpn.space/api/license/verify/test-device-id/TEST-LICE-NSE1-2345

# Test device license fetch
curl https://api.vvpn.space/api/license/device/test-device-id
```

### Step 3: Check Server Logs

```bash
# If using PM2
pm2 logs license-api

# If using systemd
journalctl -u license-api -f

# If running directly
tail -f /var/log/license-api.log
```

---

## Android App Changes Made

### Fixed Files:

1. **RetrofitClient.kt** - Disabled HTTP body logging in release builds (performance fix)
2. **MainActivity.kt** - Replaced raw Thread with Coroutines (reliability fix)

### Changes:
- ✅ HTTP logging only in debug builds
- ✅ Proper coroutine-based network calls
- ✅ Increased timeout from 10s to 30s
- ✅ Better error handling with printStackTrace()

---

## Next Steps

1. **Check your backend server** - Verify the routes exist
2. **Test the endpoints** - Use curl or Postman
3. **Rebuild Android app** - Use the fixed version
4. **Test both debug and release** - Verify license fetching works

## Questions?

If you need help setting up the backend routes, please provide:
- Backend framework (Express, Fastify, etc.)
- Backend repository location
- Current route configuration
