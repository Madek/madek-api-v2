### OpenApi 3
- [github.com/Madek/madek-api-v2](https://github.com/Madek/madek-api-v2)
- [OpenApi Specification 3.0](https://swagger.io/specification/v3/)
- [Swagger Editor for openApi](https://editor.swagger.io/?url=https://ga4gh.github.io/task-execution-schemas/openapi.yaml)

<br/>

### ZHdK
- [Medienarchiv der ZHdK](https://medienarchiv.zhdk.ch/)
- [Madek API Browser](https://medienarchiv.zhdk.ch/api/browser/)

<br/>

## Supported auth-methods
1. **Api-Token** *(provides access to public endpoints only, not to /admin/)*
2. Header: `Authorization: token <replace-this-by-token>`

<br/>

## Nice2Know
1. No ROA-support anymore
2. Coercion-errors
   1. The full error details will be logged to the console.
   2. The coercion response will return a simplified version of the error, with one of the following status codes:
      1. 422: Request error
      2. 500: Response error
3. Format-Examples
   1. ISO-Timestamp with TZ: "2025-06-02 11:13:06.264577+02"
   2. PG timestamptz: "2023-01-01T12:02:00+10"
4. Swagger-Authorize
   1. Public endpoints have no lock
   2. *Auth-endpoints* have a lock that provides
      1. **apiKey-form**
5. Roles
   1. PUBLIC....GET-ENDPOINTS
   2. USER.......PUT/DELETE/POST-ENDPOINTS (Session/Token)
   3. ADMIN.....GET/PUT/DELETE/POST-ENDPOINTS (Session/Token) 
6. Permission-Tables
   1. admins
   2. auth_systems_users
   3. collection_api_client_permission
   4. collection_user_permission
   5. media_entry_user_permissions
   6. vocabulary_user_permissions
