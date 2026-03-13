## Payload example
> PATCH /api-v2/admin/groups/{id}
---
`creator_id` and `updator_id` are optional.

```json
{
  "name": "string",
  "type": "AuthenticationGroup",
  "institutional_id": "001e6264-5d01-4c1e-a77e-063fc043c836",
  "institutional_name": "001e6264-5d01-4c1e-a77e-063fc043c836",
  "institution": "string",
  "creator_id": "7b2787e0-54bd-4eb9-89f0-2eedf64b8cc3",
  "updator_id": "7b2787e0-54bd-4eb9-89f0-2eedf64b8cc3"
}
```
