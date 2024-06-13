## Payload example
### Keep in mind to post new/unique id
CAUTION: Either :responsible_user_id OR :responsible_user_id has to be set - not both (db-constraint)
---

```json
{
  "default_resource_type": "collections",
  "get_metadata_and_previews": true,
  "is_master": true,
  "default_context_id": "columns",
  "layout": "list",
  "sorting": "manual DESC",
  "workflow_id": "5e0f0a61-746f-4823-9d9c-6d05eb4d9876",
  
  // either or
  "responsible_delegation_id": "9f52df0d-6688-4512-81f7-d4f4eb0ec6e3",
  "responsible_user_id": null

//  "responsible_delegation_id": null,
//  "responsible_user_id": "47da46e9-8a5f-4eac-a7c0-056706a70fc0"
}
```
