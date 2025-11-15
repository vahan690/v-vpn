# V-VPN Admin Panel Guide

## Access

**URL:** https://admin.vvpn.space/

**Default Login:** Use any user account from your database (admin role checking is not enforced yet)

---

## Features

### 1. Dashboard Overview

The main dashboard shows key metrics:

#### Revenue Statistics
- **Total Revenue**: All-time revenue from completed orders
- **Today's Revenue**: Revenue generated today
- **Week Revenue**: Last 7 days revenue
- **Month Revenue**: Last 30 days revenue

#### License Statistics
- **Total Licenses**: All licenses in the system
- **Active Licenses**: Currently valid and active licenses
- **Expired Licenses**: Licenses past expiry date
- **Today's Licenses**: New licenses created today

#### User Statistics
- **Total Users**: All registered users
- **Today's Users**: New registrations today
- **Week Users**: New users in last 7 days

---

### 2. Licenses Management

**Tab:** Licenses

#### View All Licenses
- Shows all licenses with pagination (20 per page)
- Displays:
  - License Key
  - User Email (or "UNBOUND" if not activated)
  - Plan (monthly/yearly)
  - Expiry Date
  - Status (Active/Expired)
  - Device ID
  - Actions

#### Search Licenses
- Search by:
  - License key
  - Device ID
  - User email
- Real-time search results
- Click "Show All" to reset

#### License Actions

**Extend License:**
- Click "Extend" button
- Enter number of days to extend
- License expiry date will be extended by specified days

**Revoke License:**
- Click "Revoke" button
- Confirms before revoking
- Sets license as inactive
- User will no longer be able to use this license

---

### 3. Users Management

**Tab:** Users

Displays all registered users with:
- User ID
- Email address
- Number of licenses owned
- Total amount spent
- Registration date

---

### 4. Payments Management

**Tab:** Payments

Shows all orders/payments with:
- Order ID
- User email
- Amount paid
- Plan purchased
- Status (completed/pending)
- Associated license key
- Payment date

---

## API Endpoints

All endpoints require JWT authentication via `Authorization: Bearer <token>` header.

### Dashboard Stats
```
GET /api/admin/dashboard/stats
```

**Response:**
```json
{
  "success": true,
  "stats": {
    "revenue": {
      "total_revenue": "100.00",
      "today_revenue": "10.00",
      "week_revenue": "50.00",
      "month_revenue": "80.00",
      "total_orders": 20
    },
    "licenses": {
      "total_licenses": 50,
      "active_licenses": 40,
      "expired_licenses": 10,
      "today_licenses": 5
    },
    "users": {
      "total_users": 100,
      "today_users": 5,
      "week_users": 20
    }
  }
}
```

### Get Licenses
```
GET /api/admin/licenses?page=1&limit=20
```

**Response:**
```json
{
  "success": true,
  "licenses": [
    {
      "license_key": "XXXX-XXXX-XXXX-XXXX",
      "email": "user@example.com",
      "plan_id": "monthly",
      "expiry_date": "2025-12-31",
      "is_active": true,
      "device_id": "abc123",
      "amount": "5.00"
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 20,
    "total": 50,
    "totalPages": 3
  }
}
```

### Search Licenses
```
GET /api/admin/licenses/search?q=search_term
```

### Revoke License
```
POST /api/admin/licenses/revoke
Content-Type: application/json

{
  "licenseKey": "XXXX-XXXX-XXXX-XXXX"
}
```

### Extend License
```
POST /api/admin/licenses/extend
Content-Type: application/json

{
  "licenseKey": "XXXX-XXXX-XXXX-XXXX",
  "days": 30
}
```

### Get Users
```
GET /api/admin/users?page=1&limit=20
```

### Get Payments
```
GET /api/admin/payments?page=1&limit=20
```

---

## Design Features

### Modern UI/UX
- Gradient background design
- Card-based layout
- Responsive design (mobile-friendly)
- Clean typography
- Smooth transitions and hover effects

### Color Coding
- **Green badges**: Active/Completed
- **Red badges**: Expired/Inactive
- **Orange badges**: Pending
- **Blue badges**: Completed payments

### Interactive Elements
- Tabbed navigation
- Real-time search
- Confirm dialogs for destructive actions
- Loading states
- Error handling

---

## Security

### Current Implementation
- JWT token-based authentication
- Tokens stored in localStorage
- Auto-logout on 401 responses
- HTTPS required (configured via nginx)

### Recommendations for Production

1. **Add Role-Based Access Control (RBAC)**
   ```sql
   ALTER TABLE users ADD COLUMN role VARCHAR(20) DEFAULT 'user';
   UPDATE users SET role = 'admin' WHERE email = 'admin@vvpn.space';
   ```

2. **Update Admin Middleware**
   ```javascript
   const requireAdmin = async (req, res, next) => {
       const userId = req.user.id;
       const result = await pool.query(
           'SELECT role FROM users WHERE id = $1',
           [userId]
       );

       if (!result.rows[0] || result.rows[0].role !== 'admin') {
           return res.status(403).json({
               success: false,
               error: 'Admin access required'
           });
       }

       next();
   };
   ```

3. **Enable HTTPS**
   - SSL certificate already configured for admin.vvpn.space
   - Nginx redirects HTTP to HTTPS

4. **Add Audit Logging**
   - Log all admin actions
   - Track who made changes and when

---

## Troubleshooting

### Issue: Can't login to admin panel
**Solution:**
- Make sure you have a user account in the database
- Check that the email/password are correct
- Verify JWT_SECRET is configured in backend .env

### Issue: Dashboard shows "Loading..."
**Solution:**
- Check browser console for errors
- Verify API endpoints are accessible
- Check nginx is running: `systemctl status nginx`
- Check license-api is running: `pm2 list`

### Issue: Data not updating
**Solution:**
- Hard refresh browser (Ctrl+Shift+R)
- Check backend logs: `pm2 logs license-api`
- Verify database connectivity

### Issue: 401 Unauthorized errors
**Solution:**
- Token might be expired
- Logout and login again
- Check JWT_SECRET matches between login and admin API

---

## Future Enhancements

### Planned Features
1. **License Generation**
   - Create new licenses manually
   - Bulk license creation
   - Import/export licenses

2. **Analytics Dashboard**
   - Revenue charts (daily/weekly/monthly)
   - User growth graphs
   - License activation trends

3. **User Management**
   - Edit user details
   - Reset passwords
   - Ban/unban users

4. **Email Notifications**
   - Notify users of license expiry
   - Send welcome emails
   - Payment confirmations

5. **Export Features**
   - Export data to CSV/Excel
   - Generate reports
   - Download user lists

6. **Advanced Filtering**
   - Filter by date range
   - Filter by status
   - Sort columns

7. **Real-time Updates**
   - WebSocket integration
   - Live payment notifications
   - Auto-refresh data

---

## Technical Stack

### Frontend
- **Pure JavaScript** (Vanilla JS)
- **No framework dependencies**
- **Responsive CSS**
- **LocalStorage for token persistence**

### Backend
- **Node.js + Express**
- **PostgreSQL database**
- **JWT authentication**
- **Rate limiting enabled**

### Infrastructure
- **Nginx reverse proxy**
- **PM2 process manager**
- **SSL/HTTPS enabled**

---

## Maintenance

### Regular Tasks

1. **Monitor Disk Space**
   ```bash
   df -h
   ```

2. **Check Backend Logs**
   ```bash
   pm2 logs license-api
   ```

3. **Database Backups**
   ```bash
   pg_dump vvpn_production > backup_$(date +%Y%m%d).sql
   ```

4. **Update Dependencies**
   ```bash
   cd /opt/license-api
   npm audit
   npm update
   pm2 restart license-api
   ```

---

## Support

For issues or feature requests:
1. Check backend logs: `ssh root@192.168.11.202 "pm2 logs license-api"`
2. Check nginx logs: `ssh root@192.168.11.202 "tail -100 /var/log/nginx/error.log"`
3. Check database: `ssh root@192.168.11.200 "su - postgres -c 'psql vvpn_production'"`

---

**Admin panel is fully functional and ready to use!** 🎉
