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
