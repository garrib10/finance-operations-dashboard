## Day 4 Summary

- Added Spring Security
- Added JWT authentication
- Added login endpoint
- Added database-backed UserDetailsService
- Added JWT authentication filter
- Added protected `/api/auth/me` endpoint
- Added Swagger Bearer authentication
- Added typed API error responses
- Added 401 handling for missing/invalid tokens

### Authentication Flow

POST /api/auth/login
→ validate credentials
→ BCrypt password verification
→ generate JWT
→ return bearer token

Protected request
→ Authorization: Bearer <JWT>
→ JWT filter
→ validate token
→ load user
→ populate SecurityContext
→ controller
