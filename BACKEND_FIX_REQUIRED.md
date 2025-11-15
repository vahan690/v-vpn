# URGENT: Backend Fix Required for License Activation

## Problem Summary

When users activate licenses (either manually or via BSC payment), the license is NOT being bound to their account in the database. This causes:

1. ✅ License shows as "activated" in the app
2. ❌ License stays "UNBOUND" in the database
3. ❌ After app restart, user must activate license again
4. ❌ Multiple users can activate the same license

## Database Evidence

```sql
license_key          | email              | device_id         | status
---------------------|--------------------|--------------------|--------
XK9P-34O7-QS1G-EILV | UNBOUND            | 0e885b14d9270b7a  | Valid
U401-97YH-B7XK-195P | vahan690@gmail.com | 69180099f28e4265  | Valid
```

The "UNBOUND" license means the backend never updated it when the user activated it.

---

## Backend Endpoint That Needs Fixing

### `POST /api/license/verify-and-link`

This endpoint is called by the Android app when a user activates a license (manually or via payment).

**Current Behavior:** ❌
- Verifies the license is valid
- Returns success
- **BUT DOES NOT UPDATE THE DATABASE**

**Required Behavior:** ✅
- Verify the license is valid
- Extract user info from JWT token
- **UPDATE the license record to bind it to the user**
- Return the updated license details

---

## Implementation Guide

### Step 1: Extract User from JWT Token

Your backend needs to decode the JWT token from the `Authorization` header:

```javascript
// Example with Express + jsonwebtoken
const jwt = require('jsonwebtoken');

app.post('/api/license/verify-and-link', authenticateToken, async (req, res) => {
    const { licenseKey, deviceId } = req.body;
    const user = req.user; // Extracted from JWT by authenticateToken middleware

    // ... rest of implementation
});

// Middleware to verify JWT
function authenticateToken(req, res, next) {
    const authHeader = req.headers['authorization'];
    const token = authHeader && authHeader.split(' ')[1]; // Bearer TOKEN

    if (!token) {
        return res.status(401).json({ error: 'No token provided' });
    }

    jwt.verify(token, process.env.JWT_SECRET, (err, user) => {
        if (err) {
            return res.status(403).json({ error: 'Invalid token' });
        }
        req.user = user; // Contains: { id, email }
        next();
    });
}
```

### Step 2: Verify License Exists and Is Valid

```javascript
// Check if license exists and is valid
const license = await db.query(
    'SELECT * FROM licenses WHERE license_key = $1',
    [licenseKey]
);

if (!license.rows.length) {
    return res.status(404).json({
        success: false,
        isValid: false,
        error: 'License key not found'
    });
}

const licenseData = license.rows[0];

// Check if license is expired
const now = new Date();
const expiryDate = new Date(licenseData.expiry_date);

if (expiryDate < now) {
    return res.status(400).json({
        success: false,
        isValid: false,
        error: 'License has expired'
    });
}
```

### Step 3: Update License to Bind to User

```javascript
// Update the license record to bind it to the user
await db.query(
    `UPDATE licenses
     SET email = $1,
         device_id = $2,
         updated_at = NOW()
     WHERE license_key = $3`,
    [user.email, deviceId, licenseKey]
);
```

### Step 4: Return Success Response

```javascript
return res.json({
    success: true,
    isValid: true,
    message: 'License activated successfully',
    license: {
        licenseKey: licenseData.license_key,
        planId: licenseData.plan_id,
        expiryDate: licenseData.expiry_date
    }
});
```

---

## Complete Implementation Example

```javascript
const express = require('express');
const jwt = require('jsonwebtoken');
const { Pool } = require('pg');

const app = express();
const db = new Pool({ /* your postgres config */ });

// Middleware
app.use(express.json());

function authenticateToken(req, res, next) {
    const authHeader = req.headers['authorization'];
    const token = authHeader && authHeader.split(' ')[1];

    if (!token) {
        return res.status(401).json({ error: 'No token provided' });
    }

    jwt.verify(token, process.env.JWT_SECRET, (err, user) => {
        if (err) {
            return res.status(403).json({ error: 'Invalid token' });
        }
        req.user = user;
        next();
    });
}

// Endpoint
app.post('/api/license/verify-and-link', authenticateToken, async (req, res) => {
    try {
        const { licenseKey, deviceId } = req.body;
        const user = req.user; // { id, email }

        console.log(`User ${user.email} attempting to activate license ${licenseKey}`);

        // 1. Check if license exists
        const licenseResult = await db.query(
            'SELECT * FROM licenses WHERE license_key = $1',
            [licenseKey]
        );

        if (!licenseResult.rows.length) {
            return res.status(404).json({
                success: false,
                isValid: false,
                error: 'License key not found'
            });
        }

        const license = licenseResult.rows[0];

        // 2. Check if license is expired
        const now = new Date();
        const expiryDate = new Date(license.expiry_date);

        if (expiryDate < now) {
            return res.status(400).json({
                success: false,
                isValid: false,
                error: 'License has expired'
            });
        }

        // 3. Check if license is already bound to a different user
        if (license.email && license.email !== 'UNBOUND' && license.email !== user.email) {
            return res.status(400).json({
                success: false,
                isValid: false,
                error: 'License is already activated by another user'
            });
        }

        // 4. Update the license to bind it to the user
        await db.query(
            `UPDATE licenses
             SET email = $1,
                 device_id = $2,
                 updated_at = NOW()
             WHERE license_key = $3`,
            [user.email, deviceId, licenseKey]
        );

        console.log(`License ${licenseKey} successfully bound to ${user.email}`);

        // 5. Return success
        return res.json({
            success: true,
            isValid: true,
            message: 'License activated successfully',
            license: {
                licenseKey: license.license_key,
                planId: license.plan_id,
                expiryDate: license.expiry_date
            }
        });

    } catch (error) {
        console.error('Error in verify-and-link:', error);
        return res.status(500).json({
            success: false,
            isValid: false,
            error: 'Internal server error'
        });
    }
});
```

---

## Request/Response Format

### Request

```http
POST /api/license/verify-and-link HTTP/1.1
Host: api.vvpn.space
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

{
  "licenseKey": "XK9P-34O7-QS1G-EILV",
  "deviceId": "0e885b14d9270b7a"
}
```

### Success Response (200)

```json
{
  "success": true,
  "isValid": true,
  "message": "License activated successfully",
  "license": {
    "licenseKey": "XK9P-34O7-QS1G-EILV",
    "planId": "monthly",
    "expiryDate": "2025-12-14T18:04:11.851Z"
  }
}
```

### Error Response (400)

```json
{
  "success": false,
  "isValid": false,
  "error": "License is already activated by another user"
}
```

---

## Testing the Fix

### 1. Test with curl

```bash
# First, login to get a JWT token
TOKEN=$(curl -s -X POST https://api.vvpn.space/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"chuvak@test.test","password":"password123"}' \
  | jq -r '.token')

# Then, activate a license
curl -X POST https://api.vvpn.space/api/license/verify-and-link \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "licenseKey": "XK9P-34O7-QS1G-EILV",
    "deviceId": "test-device-123"
  }'
```

### 2. Verify in Database

```sql
SELECT license_key, email, device_id, plan_id, expiry_date
FROM licenses
WHERE license_key = 'XK9P-34O7-QS1G-EILV';
```

Expected result:
```
license_key          | email              | device_id        | plan_id | expiry_date
---------------------|--------------------|--------------------|---------|------------------
XK9P-34O7-QS1G-EILV | chuvak@test.test   | test-device-123    | monthly | 2025-12-14...
```

**Before Fix:** email would be "UNBOUND"
**After Fix:** email should be "chuvak@test.test"

---

## Android App Changes (Already Done)

The Android app has been updated to:
✅ Use `verifyAndLinkLicense` instead of `verifyLicense`
✅ Send JWT token in Authorization header
✅ Send deviceId and licenseKey in request body

---

## Summary

**What needs to be done:**

1. ✅ Add `authenticateToken` middleware to extract user from JWT
2. ✅ Update `/api/license/verify-and-link` endpoint to:
   - Verify license exists and is valid
   - Check if already bound to different user
   - **UPDATE database to bind license to user**
   - Return success with license details

**After this fix:**
- Users will be able to activate licenses successfully
- Licenses will persist after app restart
- Each license can only be activated by one user
- Database will show correct user email for each license

---

## Need Help?

If you need assistance implementing this, please share:
1. Your backend framework (Express, Fastify, etc.)
2. Your current `/api/license/verify-and-link` implementation
3. Your JWT authentication middleware
