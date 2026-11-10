# TODO: api-v2 access parity with the legacy API

The specs in this folder check api-v2 against the access rules of the legacy
API (`../api`, v1). Policy: v2 must grant what v1 grants and deny what v1
denies. Where v1 leaks data (open indexes, unauthorized meta-data-people),
v2 only has to be at least as strict.

Run them against a local server on port 3104:

```sh
mise exec -- bundle exec rspec spec/api/
```

Status: 271 examples, 24 failures. Every failure below is a real divergence
in api-v2, not a spec bug.

## 1. DONE: Admin bypass on media resources

Admins without any permission used to get 200 on private media-entries,
collections, meta-data, meta-data data-streams, meta-datum-person, previews
and `/media-entries/:id/media-files/data-stream/`, and the indexes, arcs and
full_texts listed private resources to them. v1 does not privilege admins.

Fixed by scoping the admin privilege to admin routes:

- `src/madek/api/utils/auth.clj`: `wrap-authorize-admin!` marks the request
  with `:admin-route-authorized`; `auth/admin-route-authorized?` reads it.
- `authorize-request-for-media-resource` (`shared/media_resource_helper.clj`),
  both `filter-by-view-permission` functions and the view filters of
  `collection_media_entry_arcs.clj`, `collection_collection_arcs.clj` and
  `full_texts.clj` bypass only when `admin-route-authorized?`, no longer on
  `:is_admin`.
- `/api-v2/admin/media-entries/`, `/api-v2/admin/collections/` and
  `PUT /api-v2/admin/collections/:id` keep the admin privilege.

Covered by the admin variants of every condition in `shared.rb`
(`ApiAccessParity::ADMIN_VARIANTS`) and by `admin_access_spec.rb` (session
auth, write scopes, arcs, full_texts, admin routes).

Not changed: `permissions/put.clj` still lets admins change the
responsibility of a resource, but only after they passed the
edit-permissions check like any other user.

## 2. DONE: `/media-entries/:id/media-files/data-stream/` denied anonymous on public full-size

Anonymous got 401 on an entry with `get_metadata_and_previews` and
`get_full_size` set, because `jqh/ring-wrap-authorization-download` never
short-circuits on public for `:download`.

Fixed in `src/madek/api/resources/media_files.clj` (`media-entry-routes`):
the route uses `media-files.authorization/wrap-auth-media-file-full_size`
like `/media-files/:id/data-stream/`, so both data-stream routes share the
same code path (public `get_full_size` -> 200, else 401/403 by download
permission).

## 3. DONE (deliberate divergence): `/groups/` and `/groups/:id` were public

v1 serves groups only to admins. Decision: keep the non-admin routes for
authenticated users (e.g. picking a group when editing permissions) and
deny anonymous only.

Fixed:

- `src/madek/api/resources/groups.clj` `user-routes` use
  `madek.api.authorization/wrap-authorized-user` (401 for anonymous).
- The two `reference-data` entries were removed from `route_authorization.clj`.
- `admin_only_access_spec.rb` checks anonymous 401, user 200, admin 200 for
  these two routes; the `/admin/groups/...` routes stay admin-only.
- `spec/resources/audits/vocabulary_spec.rb` and
  `spec/authentication/public_user_admin_token_spec.rb` expect 401 for
  anonymous `/groups/`.

## 4. DONE: `/people/:id` exposed institutional fields

Anonymous and regular users additionally got `institutional_id`,
`institutional_directory_infos` and `institutional_directory_inactive_since`.

Fixed: the three keys were added to `internal-people-keys`
(`people/common.clj`) and removed from `public-schema` (`people/get.clj`).
Lookup by `[institution, institutional_id]` still works; admin routes keep
all fields.

## 5. WONTFIX (deliberate divergence): no public `/delegations/:id`

v1 serves `/delegations/:id` to everybody (id, name, description). api-v2
keeps delegations admin-only (`/api-v2/admin/delegations/:id`).
`public_resources_access_spec.rb` documents this: anonymous and users get
no delegation data, `/admin/delegations/:id` is 403 for users, 200 for admins.

## DONE: Related bug, meta-datum-person 500

`GET .../meta-data/:meta_key_id/meta-data-people/:meta_datum_person_id`
answered 500: `handle_get-meta-datum-person` did `(:person_id ...)` on the
path UUID. Its `/person` and `/role` sub-routes looked the row up without
scoping it to the authorized media-resource, so a meta-datum-person of a
private entry was readable through any public entry with the same meta-key.

Fixed in `src/madek/api/resources/meta_data/get.clj`: `find-meta-datum-person`
looks the `meta_data_people` row up by id and by the meta-datum of the
authorized media-resource; all three handlers use it and answer 404 when it
(or the role) is missing. The base route returns the row
(`MetaDatumPersonSchema`).

Covered in `media_resources_access_spec.rb` ("with a meta-datum-person"):
the access matrix for the base route and `/person`, `/role` with and
without a role, and 404 when reaching another entry's meta-datum-person.
