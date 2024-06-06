[Swagger Specification 2.0](https://swagger.io/docs/specification/2-0/basic-structure/)
[Swagger Editor](https://editor.swagger.io/)

<br/>




## Old madek-api
- https://medienarchiv.zhdk.ch/
- https://medienarchiv.zhdk.ch/api/browser/


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
1. No ROA-support

