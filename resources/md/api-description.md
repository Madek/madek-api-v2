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
