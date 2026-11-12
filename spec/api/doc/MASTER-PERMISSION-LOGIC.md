# Permission logic on `master` (non-`/admin` endpoints)

Analysed at `master` @ `c8f616d`. The code was analysed and the results were verified at
runtime: the jar was built from `master` and probed with throw-away specs against a private
media entry owned by another user.

## Main question

> Is an admin allowed to modify entries of other users on non-`/admin` endpoints?

**Yes.** On `master`, an admin token (or admin session) bypasses every media-resource
permission check on the user routes (`/api-v2/media-entries/...`, `/api-v2/collections/...`).
An admin without any responsibility or permission on a private entry of another user can:

- read it, and download it via `/media-entries/:id/media-files/data-stream/`
- edit meta-data, full texts, custom URLs, edit sessions and collection arcs
- list and manage conf-links and user/group permissions
- make it public or change any of its permissions
- **take over responsibility** (`PUT .../perms/resources/ {responsible_user_id: <admin>}`)
- **delete** it

The API v1 has no such bypass (see [Comparison with API v1](#comparison-with-api-v1-api)).

## How access is decided

### 1. Authentication sets `:is_admin`

| Source | Code |
|---|---|
| Token | `src/madek/api/authentication/token.clj:60-78`: `:is_admin (sd/is-admin user-id)`, a row in `admins` |
| Session | `src/madek/api/authentication/session.clj:75`: same |
| `/api-v2/admin/` guard | `token.clj:70`: a non-admin token gets 403 there. User routes are not restricted. |

### 2. Central check: `authorize-request-for-media-resource`

`src/madek/api/resources/shared/media_resource_helper.clj:49-79`. All `jqh/ring-wrap-authorization-*`
wrappers end up here:

```clojure
(if (and (= scope :view) (public? media-resource))
  (handler request)                                   ; public view
  (if-let [auth-entity (-> request :authenticated-entity)]
    (if (-> request :is_admin true?)
      (handler request)                               ; <-- ADMIN BYPASS, any scope
      (if (authorized? auth-entity media-resource scope tx)
        (handler request)
        {:status 403}))
    {:status 401}))
```

Scopes: `:view`, `:download`, `:edit-md`, `:edit-perm` (`src/madek/api/authorization.clj`).

### 3. Permission predicates for non-admins

`src/madek/api/resources/media_resources/permissions.clj`

`permission-by-auth-entity?` (line 181) is used for `:view`, `:download` and `:edit-md`. It is
granted if **any** of the following is true:

1. the public flag on the resource (`get_metadata_and_previews`, `get_full_size`); there is no
   public flag for edit
2. the user is the `responsible_user_id`
3. the user is a member of the `responsible_delegation_id`, directly (`delegations_users`) or
   through a group (`delegations_groups` → `groups_users`), via `c/delegation-ids`
4. a user permission with the flag set
5. a group permission with the flag set (the user is in the group)

`edit-permissions-by-auth-entity?` (line 199) is used for `:edit-perm`. It allows only 2, 3 and 4.
**Group permissions do not grant `edit_permissions`.**

Permission flags per resource type:

| Scope | MediaEntry flag | Collection flag |
|---|---|---|
| `:view` | `get_metadata_and_previews` | `get_metadata_and_previews` |
| `:download` | `get_full_size` | – |
| `:edit-md` | `edit_metadata` | `edit_metadata_and_relations` |
| `:edit-perm` | `edit_permissions` | `edit_permissions` |

### 4. Endpoint → scope (user routes)

| Endpoint (media entry; collections are analogous) | Scope |
|---|---|
| `GET /media-entries/:id`, `.../meta-data/...`, `.../previews/`, `.../media-files/`, edit-session list | `:view` |
| `GET /media-entries/:id/media-files/data-stream/` | `:download` |
| `POST/PUT/DELETE .../meta-data/...`, `.../full_texts/`, `.../custom_urls/`, `POST .../edit_sessions/`, `PUT .../publish/` | `:edit-md` |
| `POST/PUT/DELETE /collections/:id/media-entry-arcs/:me_id` (only the **collection** is checked) | `:edit-md` |
| `DELETE /media-entries/:id`, `DELETE /collections/:id` | `:edit-perm` |
| `.../conf-links/` (all methods) | `:edit-perm` |
| `.../perms/` writes, incl. `responsible_user_id`/`responsible_delegation_id` changes | `:edit-perm` |
| `GET .../perms/resources/`, own perms | `:view` |
| `GET .../perms/users/`, `.../perms/groups/` lists | `:edit-perm` / `:view` (see `permissions/get.clj`) |

## Matrix: who may do what on someone else's entry

✅ = allowed, ❌ = 403. "Admin" means a user listed in the `admins` table.

| Actor | view | download | edit meta-data | edit perms / conf-links | change responsible | delete |
|---|---|---|---|---|---|---|
| Plain user (no relation) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Responsible user | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Delegation member (direct) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ¹ |
| Delegation member (via group) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ¹ |
| User permission (all flags) | ✅ | ✅ | ✅ | ✅ | ✅ ² | ✅ ² |
| Group permission (all flags) | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| **Admin, no relation** | ✅ | ✅ ³ | ✅ | ✅ | ✅ | ✅ |
| **Admin, view-only permission** | ✅ | ✅ ³ | ✅ | ✅ | ✅ | ✅ |
| Admin, user permission (all flags) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

1. Authorization passes, but deleting a delegation-owned entry returns **500**: the response
   coercion of `DELETE /media-entries/:id` requires `responsible_user_id` to be a UUID
   (`reitit.coercion/response-coercion` error in the server log). This is a bug, not an access
   decision.
2. `edit_permissions` alone is enough to change `responsible_user_id`, so a user who was merely
   granted permissions can **take over responsibility** and then lock out the owner.
3. Only via `/media-entries/:id/media-files/data-stream/`. The alternative route
   `/media-files/:id/data-stream/` uses its own check
   (`src/madek/api/resources/media_files/authorization.clj:9`) **without** the admin bypass and
   returns 403.

### Probe output (`master`, HTTP status codes)

```
plain_user              view 403 dl_me 403 dl_mf 403 edit_md 403 conf 403 perms 403 public 403 take_resp 403 delete 403
responsible_user        view 200 dl_me 200 dl_mf 200 edit_md 200 conf 200 perms 200 public 200 take_resp 200 delete 200
delegation_member       view 200 dl_me 200 dl_mf 200 edit_md 200 conf 200 perms 200 public 200 take_resp 200 delete 200
delegation_group_member view 200 dl_me 200 dl_mf 200 edit_md 200 conf 200 perms 200 public 200 take_resp 200 delete 200
user_permission         view 200 dl_me 200 dl_mf 200 edit_md 200 conf 200 perms 200 public 200 take_resp 200 delete 200
group_permission        view 200 dl_me 200 dl_mf 200 edit_md 200 conf 403 perms 200 public 403 take_resp 403 delete 403
admin_plain             view 200 dl_me 200 dl_mf 403 edit_md 200 conf 200 perms 200 public 200 take_resp 200 delete 200
admin_view_permission   view 200 dl_me 200 dl_mf 403 edit_md 200 conf 200 perms 200 public 200 take_resp 200 delete 200
admin_user_permission   view 200 dl_me 200 dl_mf 200 edit_md 200 conf 200 perms 200 public 200 take_resp 200 delete 200
```

The entry was private (`get_metadata_and_previews: false`, `get_full_size: false`) and had an
image media file. The probe calls were:

| Column | Request |
|---|---|
| `edit_md` | `POST .../full_texts/` |
| `conf` | `GET .../conf-links/` |
| `perms` | `GET .../perms/users/` |
| `public` | `PUT .../perms/resources/ {get_metadata_and_previews: true}` |
| `take_resp` | `PUT .../perms/resources/ {responsible_user_id: <actor>, responsible_delegation_id: nil}` |

## Further findings (not admin-specific)

| # | Finding | Where |
|---|---|---|
| 1 | **Collection-collection arcs have no authorization at all.** A plain user, and even an **unauthenticated** request, can create, update and delete arcs between foreign private collections (probe: 200 for all). | `src/madek/api/resources/collection_collection_arcs.clj:181-260` (`; TODO add permission checks`) |
| 2 | Creating a collection-media-entry arc checks only the collection (`:edit-md`), not the media entry. | `collection_media_entry_arcs.clj:204-253` |
| 3 | Listings (`GET /media-entries/`, `GET /collections/`) have no default permission filter; they filter only when `me_*` / `public_*` query params are given. `:is_admin` is not consulted, so admins and users are treated the same here. | `media_entries/query.clj:185-197`, `collections/index.clj:37-50` |
| 4 | `wrap-authorize-http-method` (blocks writes for session auth) is defined but not used anywhere, so session-authenticated writes are possible (CSRF protected). | `src/madek/api/authorization.clj` |
| 5 | Changing responsibility needs only `:edit-perm`, so any user with an `edit_permissions` user permission can do it (see note 2 above). | `src/madek/api/resources/permissions/put.clj` |
| 6 | `DELETE /media-entries/:id` returns 500 for delegation-owned entries (response coercion). | `media_entries.clj` delete route, `schema_media_entry_deleted` |

## Comparison with API v1 (`../api`)

- v1 authorizes only **GET** on `/media-entries/:id*`, `/collections/:id*`, `/meta-data/:id*` and
  `/previews/:id*` (`../api/src/madek/api/resources.clj:120-147`).
- The decision is: public → allow, else `authorized?` (view permission via responsible
  user / delegation / user / group / api-client permission).
- **No `is_admin` check.** An admin on v1 user routes sees only what they are entitled to as a
  user. `is_admin` in v1 is used only for `/admin`-style checks (`utils/auth.clj`) and the
  people representation.
- The admin bypass in `media_resource_helper.clj:63` is therefore an **api-v2-only privilege** and
  a divergence from v1.

## Summary

| | Plain user | Delegated user | User with permission | Admin (no relation) |
|---|---|---|---|---|
| Basis of access | public flags | membership in the responsible delegation (direct or via group) = same as the responsible user | flags of the user / group permission (groups: no `edit_permissions`) | `:is_admin` → everything |
| Modify foreign entries on user routes | ❌ | ✅ (delegation's entries) | ✅ per flag | **✅ always** |

## Assessment: is the admin bypass on user endpoints best practice?

**No.** Common practice is that an admin role does not give implicit rights on user endpoints:

- **Least privilege:** a leaked admin token can take over or delete any user's content through
  the normal user endpoints.
- **Explicit intent:** admin actions belong under `/api-v2/admin/...`, where they are clearly
  marked, documented and easy to restrict (e.g. by network, extra auth or rate limits).
- **Auditability:** on user routes an admin edit is indistinguishable from a regular user edit
  in the logs.
- **Consistency:** API v1 has no such privilege, and `master` itself is inconsistent:
  `/media-files/:id/data-stream/` returns 403 for admins, while
  `/media-entries/:id/media-files/data-stream/` allows them.

**Recommendation:** admins act as normal users on user routes and need responsibility or
permissions like everyone else. To act on someone else's resource, an admin should either:

- use a dedicated `/api-v2/admin/...` endpoint,
- grant themselves an explicit permission, or
- take over responsibility through an admin route that is recorded in the audit log.

---

# FEATURE-BRANCH-LOGIC

Branch `mr/pr/minor-opt`. **Rule: an admin can modify resources of other users only on
`/api-v2/admin/...` routes. On user routes an admin is treated like every other user** and needs
responsibility (direct or via delegation) or a permission.

## How it is enforced

| Mechanism | Code |
|---|---|
| `:is_admin` is still set by authentication (token/session), but **no user-route authorization reads it** anymore. Only authentication and `authorize-admin!` use it. | `authentication/token.clj:61-79`, `authentication/session.clj:77` |
| `wrap-authorize-admin!` (only on `/admin` routes) sets `:admin-route-authorized true`. The flag is internal to the request map; HTTP input cannot set it. | `src/madek/api/utils/auth.clj:12-37` |
| `admin-route-authorized?` replaces the `:is_admin` bypass in the central media-resource check. | `shared/media_resource_helper.clj:62` |
| The same flag is used for listings and derived resources: media-entry and collection view filters, collection-media-entry arcs, collection-collection arcs, full texts. | `media_entries/advanced_filter/permissions.clj:85`, `collections/advanced_filter/permissions.clj:73`, `collection_media_entry_arcs.clj:23`, `collection_collection_arcs.clj:30`, `full_texts.clj:29` |
| Changing `responsible_user_id` / `responsible_delegation_id` / `clipboard_user_id` additionally requires being the responsible user or a member of the responsible delegation; `edit_permissions` alone is not enough, and being an admin grants nothing. | `permissions/put.clj:110-121` (`wrap-authorize-responsibility-change`) |
| Collection-collection arc writes are authorized: `:edit-md` on the parent and `:view` on the child (no longer open to anonymous). | `collection_collection_arcs.clj:55-66` |

## Matrix: who may modify someone else's entry (feature branch)

✅ = allowed, ❌ = 403.

| Actor | User routes: edit meta-data | User routes: edit perms / conf-links / delete | User routes: change responsible | `/admin` routes |
|---|---|---|---|---|
| Plain user (no relation) | ❌ | ❌ | ❌ | ❌ |
| Responsible user | ✅ | ✅ | ✅ | ❌ |
| Delegation member (direct / via group) | ✅ | ✅ | ✅ | ❌ |
| User permission (all flags) | ✅ | ✅ | ❌ | ❌ |
| Group permission (all flags) | ✅ | ❌ | ❌ | ❌ |
| **Admin, no relation** | **❌** | **❌** | **❌** | ✅ |
| **Admin, view-only permission** | **❌** | **❌** | **❌** | ✅ |
| Admin with user permission / responsibility | like a user with the same permission / responsibility | | | ✅ |

## Difference to `master`

| Action by an admin without relation to a private entry | `master` | feature branch |
|---|---|---|
| `GET /media-entries/:id` | 200 | 403 |
| `POST .../meta-data/...`, `.../full_texts/` | 200 | 403 |
| `POST/GET .../conf-links/` | 200 | 403 |
| `PUT .../perms/resources/` (make public) | 200 | 403 |
| `PUT .../perms/resources/` (take over responsibility) | 200 | 403 |
| `POST .../perms/users/:own_id` (grant oneself) | 200 | 403 |
| `DELETE /media-entries/:id`, `DELETE /collections/:id` | 200 | 403 |
| `PUT /collections/:id`, collection arcs | 200 | 403 |
| `PUT /admin/media-entries/:id` (soft delete) | 500 ¹ | 200 |
| `PUT /admin/collections/:id`, `POST/PUT/DELETE /admin/full_texts/:id` | 200 | 200 |

1. A bug on both branches, fixed here (see [Fix found while testing](#fix-found-while-testing)).

## Tests

`spec/api/admin_modification_scope_spec.rb` ensures the rule.

**User routes.** Each modification runs three times:

1. as the responsible user (control: 200 and the data changed, so the request itself is valid)
2. as an admin without relation (403, data unchanged)
3. as an admin with a view-only user permission (403, data unchanged)

| Request | Required permission |
|---|---|
| `POST /media-entries/:id/meta-data/:meta_key_id/json/` | `edit_metadata` |
| `POST /media-entries/:id/full_texts/` | `edit_metadata` |
| `POST /media-entries/:id/conf-links/` | `edit_permissions` |
| `PUT /media-entries/:id/perms/resources/` (make public) | `edit_permissions` |
| `PUT /media-entries/:id/perms/resources/` (take over responsibility) | responsibility |
| `POST /media-entries/:id/perms/users/:user_id` (grant oneself) | `edit_permissions` |
| `DELETE /media-entries/:id` | `edit_permissions` |
| `PUT /collections/:id` | `edit_metadata_and_relations` |
| `POST /collections/:id/collection-arcs/:child_id` | `edit_metadata_and_relations` |
| `POST /collections/:id/media-entry-arcs/:media_entry_id` | `edit_metadata_and_relations` |
| `PUT /collections/:id/perms/resources/` (take over responsibility) | responsibility |
| `DELETE /collections/:id` | `edit_permissions` |

**Admin routes.** The responsible user gets 403 and the data stays unchanged. An admin without
relation gets 200 and the data is changed.

- `PUT /admin/media-entries/:id` (soft delete)
- `PUT /admin/collections/:id`
- `POST /admin/full_texts/:id`
- `PUT /admin/full_texts/:id`
- `DELETE /admin/full_texts/:id`

**Related specs:**

- `spec/api/admin_access_spec.rb`: session admin, listings and derived resources
- `spec/api/media_resources_access_spec.rb`: read matrix with the admin variants
- `spec/shared/resource_access.rb` and the legacy specs: user/admin × responsible / permission /
  no permission

**Result:** `spec/api/admin_modification_scope_spec.rb` has 41 examples and 0 failures. Against
`master`, the admin examples on user routes would fail: the master probe above shows 200 for
the same requests.

## Fix found while testing

`PUT /api-v2/admin/media-entries/:id` (`{deleted_at: ...}`) always returned **500**, including
on `master`. The update succeeded, but the response schema `schema_export_adm_media_entry`
declared `deleted_at` as `IsoOffsetDateTimeString` while the DB returns an `Instant`, so the
transaction was rolled back.

Fixed in `src/madek/api/resources/media_entries.clj`: `deleted_at` is now `s/Any` in the
response schema, like the other timestamps. No spec covered this route before.
