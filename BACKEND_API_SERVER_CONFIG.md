# Backend API: Server Configuration Endpoint

## Overview
This document describes the new backend API endpoint required to serve VPN server configuration dynamically to the Android app, replacing hardcoded credentials.

---

## Endpoint

### GET /api/server/config

**Purpose:** Fetch VPN server configuration for authenticated users

**Authentication:** Required (JWT Bearer token)

**Headers:**
```
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

---

## Request

### HTTP Method
```
GET
```

### URL
```
https://api.vvpn.space/api/server/config
```

### Headers
```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### No Body Required
This is a GET request with no request body.

---

## Response

### Success Response (200 OK)

```json
{
  "success": true,
  "serverAddress": "62.171.179.248",
  "serverPort": "22153",
  "authPayload": "KKX7uSdSG8K3g54d5fh4",
  "obfuscation": "IranSafeNet2025",
  "sni": "",
  "allowInsecure": true
}
```

### Response Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `success` | boolean | Yes | Indicates if the request was successful |
| `serverAddress` | string | Yes | VPN server IP address or hostname |
| `serverPort` | string | Yes | VPN server port number (as string) |
| `authPayload` | string | Yes | Authentication payload for Hysteria2 protocol |
| `obfuscation` | string | Yes | Obfuscation password for traffic masking |
| `sni` | string | No | Server Name Indication (can be empty) |
| `allowInsecure` | boolean | No | Allow insecure TLS connections (default: true) |

---

## Error Responses

### 401 Unauthorized - Invalid/Missing Token
```json
{
  "success": false,
  "error": "Authentication required"
}
```

### 403 Forbidden - Expired License
```json
{
  "success": false,
  "error": "Your license has expired. Please renew to continue."
}
```

### 500 Internal Server Error
```json
{
  "success": false,
  "error": "Failed to fetch server configuration"
}
```

---

## Implementation Example (Node.js/Express)

```javascript
const express = require('express');
const router = express.Router();
const { authenticateToken } = require('../middleware/auth');
const pool = require('../db');

// GET /api/server/config
router.get('/config', authenticateToken, async (req, res) => {
    try {
        const userId = req.user.id;

        // Check if user has valid license
        const licenseCheck = await pool.query(`
            SELECT l.license_key, l.expiry_date, l.is_active
            FROM licenses l
            WHERE l.user_id = $1
              AND l.is_active = true
              AND l.expiry_date > NOW()
            LIMIT 1
        `, [userId]);

        if (licenseCheck.rows.length === 0) {
            return res.status(403).json({
                success: false,
                error: 'No valid license found. Please purchase or renew your license.'
            });
        }

        // Get server configuration
        // Option 1: Return hardcoded config (simplest)
        const serverConfig = {
            success: true,
            serverAddress: process.env.VPN_SERVER_ADDRESS || "62.171.179.248",
            serverPort: process.env.VPN_SERVER_PORT || "22153",
            authPayload: process.env.VPN_AUTH_PAYLOAD || "KKX7uSdSG8K3g54d5fh4",
            obfuscation: process.env.VPN_OBFUSCATION || "IranSafeNet2025",
            sni: process.env.VPN_SNI || "",
            allowInsecure: true
        };

        // Option 2: Load from database (for multi-server support)
        /*
        const serverQuery = await pool.query(`
            SELECT server_address, server_port, auth_payload,
                   obfuscation, sni, allow_insecure
            FROM server_configs
            WHERE is_active = true
            ORDER BY priority ASC
            LIMIT 1
        `);

        if (serverQuery.rows.length === 0) {
            return res.status(500).json({
                success: false,
                error: 'No server configuration available'
            });
        }

        const server = serverQuery.rows[0];
        const serverConfig = {
            success: true,
            serverAddress: server.server_address,
            serverPort: server.server_port,
            authPayload: server.auth_payload,
            obfuscation: server.obfuscation,
            sni: server.sni || "",
            allowInsecure: server.allow_insecure
        };
        */

        // Log access for monitoring
        await pool.query(`
            INSERT INTO server_config_access_log (user_id, accessed_at)
            VALUES ($1, NOW())
        `, [userId]);

        res.json(serverConfig);

    } catch (error) {
        console.error('Server config error:', error);
        res.status(500).json({
            success: false,
            error: 'Failed to fetch server configuration'
        });
    }
});

module.exports = router;
```

---

## Security Considerations

### ✅ **Implemented in Android App:**
1. JWT authentication required
2. Fallback to hardcoded config if API fails (temporary - for smooth transition)
3. 30-second timeout on API request
4. Error handling with graceful degradation

### ⚠️ **Backend Must Implement:**
1. **Rate Limiting:** Limit requests per user/IP (e.g., 10 requests per hour)
2. **License Validation:** Only serve config to users with valid, active licenses
3. **Access Logging:** Track who accesses server configs and when
4. **IP Whitelisting (Optional):** Restrict API access to specific regions
5. **Config Rotation:** Ability to update server credentials without app update
6. **Multi-Server Support:** Serve different servers to different users/plans

---

## Database Schema (Optional - for Multi-Server)

```sql
-- Server configurations table
CREATE TABLE server_configs (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    server_address VARCHAR(255) NOT NULL,
    server_port VARCHAR(10) NOT NULL,
    auth_payload VARCHAR(255) NOT NULL,
    obfuscation VARCHAR(255) NOT NULL,
    sni VARCHAR(255) DEFAULT '',
    allow_insecure BOOLEAN DEFAULT true,
    is_active BOOLEAN DEFAULT true,
    priority INTEGER DEFAULT 0,
    max_users INTEGER DEFAULT 1000,
    current_users INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Access logging table
CREATE TABLE server_config_access_log (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id),
    server_config_id INTEGER REFERENCES server_configs(id),
    accessed_at TIMESTAMP DEFAULT NOW(),
    ip_address INET
);

-- Insert default server
INSERT INTO server_configs (
    name, server_address, server_port, auth_payload,
    obfuscation, sni, is_active, priority
) VALUES (
    'Primary Server',
    '62.171.179.248',
    '22153',
    'KKX7uSdSG8K3g54d5fh4',
    'IranSafeNet2025',
    '',
    true,
    1
);
```

---

## Environment Variables (.env)

Add these to your backend `.env` file:

```env
# VPN Server Configuration (Option 1: Hardcoded in env)
VPN_SERVER_ADDRESS=62.171.179.248
VPN_SERVER_PORT=22153
VPN_AUTH_PAYLOAD=KKX7uSdSG8K3g54d5fh4
VPN_OBFUSCATION=IranSafeNet2025
VPN_SNI=
```

---

## Benefits of This Approach

### 🔐 **Security**
- ✅ Server credentials not hardcoded in APK
- ✅ Can rotate credentials instantly without app update
- ✅ Can revoke access for compromised credentials
- ✅ Can track who accesses server configs

### 🚀 **Scalability**
- ✅ Easy to add more VPN servers
- ✅ Load balancing by serving different servers to different users
- ✅ Geographic distribution (serve nearest server)
- ✅ A/B testing with different server configurations

### 🛡️ **Anti-Cracking**
- ✅ Makes cracking MUCH harder (need to crack both app AND backend)
- ✅ Can detect and block stolen tokens
- ✅ Can change credentials if cracks appear
- ✅ Rate limiting prevents mass credential harvesting

### 📊 **Monitoring**
- ✅ Track server config access patterns
- ✅ Detect suspicious activity
- ✅ Monitor server load and distribute users

---

## Deployment Checklist

### Backend:
- [ ] Implement `/api/server/config` endpoint
- [ ] Add license validation check
- [ ] Implement rate limiting (e.g., 10 req/hour per user)
- [ ] Add access logging
- [ ] Test with valid JWT tokens
- [ ] Test error cases (expired license, invalid token)
- [ ] Deploy to production server

### Android App:
- [x] Add `fetchServerConfig()` to PaymentManager
- [x] Update ProfileManager to use API config
- [x] Add fallback to hardcoded config (temporary)
- [x] Pass JWT token from MainActivity
- [ ] Build and test release APK
- [ ] Verify API integration works
- [ ] Remove hardcoded fallback (after backend deployed)

### Post-Deployment:
- [ ] Monitor API access logs
- [ ] Watch for unusual patterns
- [ ] Plan credential rotation schedule (monthly recommended)
- [ ] Set up alerts for high API error rates

---

## Next Steps

1. **Implement backend endpoint** on `api.vvpn.space`
2. **Test with Postman/curl** using valid JWT token
3. **Deploy to production**
4. **Test Android app** to verify it fetches config from API
5. **Monitor logs** for first 24 hours
6. **Remove fallback config** from app after confirming API stability
7. **Schedule first credential rotation** in 30 days

---

## Testing

### Test with curl:

```bash
# Get a valid JWT token first (from login)
TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

# Test the endpoint
curl -X GET https://api.vvpn.space/api/server/config \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
```

### Expected Response:
```json
{
  "success": true,
  "serverAddress": "62.171.179.248",
  "serverPort": "22153",
  "authPayload": "KKX7uSdSG8K3g54d5fh4",
  "obfuscation": "IranSafeNet2025",
  "sni": "",
  "allowInsecure": true
}
```

---

## Support

If you encounter issues:
1. Check API logs on backend server
2. Check Android app logs: `adb logcat | grep "ProfileManager\|PaymentManager"`
3. Verify JWT token is valid and not expired
4. Ensure user has active license
5. Test API endpoint manually with curl

---

**Document Version:** 1.0
**Last Updated:** 2025-11-15
**Author:** Claude Code
