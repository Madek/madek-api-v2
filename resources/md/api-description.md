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
2. Swagger-Authorize
   1. Public endpoints have no lock
   2. *Auth-endpoints* have a lock that provides
      1. **apiKey-form**
3. Roles
   1. PUBLIC....GET-ENDPOINTS
   2. USER.......PUT/DELETE/POST-ENDPOINTS (Session/Token)
   3. ADMIN.....GET/PUT/DELETE/POST-ENDPOINTS (Session/Token) 
4. Permission-Tables
   1. admins
   2. auth_systems_users
   3. collection_api_client_permission
   4. collection_user_permission
   5. media_entry_user_permissions
   6. vocabulary_user_permissions
