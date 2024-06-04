[Swagger Specification 2.0](https://swagger.io/docs/specification/2-0/basic-structure/)
[Swagger Editor](https://editor.swagger.io/)

<br/>

## Old madek-api
- https://medienarchiv.zhdk.ch/
- https://medienarchiv.zhdk.ch/api/browser/

<br/>

## Supported auth-methods
1. **Basic auth**
   1. username *(email OR login)*
   2. pw


2. **Api-Token** *(provides access to public endpoints only, not to /admin/)*
   2. Insert token into basicAuth.username
   2. Insert token into basicAuth.pw
   3. Header: `Authorization: token replace-this-by-token`

<br/>

## Nice2Know
1. No ROA-support anymore
2. Swagger-Authorize
   1. Public endpoints should have no lock
   2. *Auth-endpoints* should have a lock that provides
      1. **apiKey-form**
      2. **basicAuth-form**
   3. *Admin-Endpoints* should have a lock that provides **basicAuth-form only**


