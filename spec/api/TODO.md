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

## 1. Admin bypass on media resources (14 failures)

Admins without any permission get 200 on private media-entries,
collections, meta-data, meta-data data-streams, meta-datum-person, previews
and `/media-entries/:id/media-files/data-stream/`. The indexes
`/media-entries/` and `/collections/` also list private resources to them.
v1 does not privilege admins and answers 403.

Failing specs: `admin_without_permission` examples in
`media_resources_access_spec.rb` and `indexes_access_spec.rb`.

Where:

- `src/madek/api/resources/shared/media_resource_helper.clj:61`:
  `authorize-request-for-media-resource` has an `(if (-> request :is_admin true?) ...)` branch.
- `src/madek/api/resources/media_entries/advanced_filter/permissions.clj:84`
  and `src/madek/api/resources/collections/advanced_filter/permissions.clj:72`:
  `filter-by-view-permission` returns the unfiltered query for `is_admin`.

Suggested fix:

- Remove the `is_admin` branch in `authorize-request-for-media-resource`, so
  admins fall through to `authorized?` (permission, responsibility, delegation).
- Remove the `(true? is_admin) sqlmap` clause in both `filter-by-view-permission`
  functions.
- If admins need full access (e.g. for support), expose it explicitly under
  `/api-v2/admin/...` routes guarded by `wrap-authorize-admin!` rather than on
  the user routes. `/media-files/:id` already behaves correctly via
  `media_files/authorization.clj`, which has no admin bypass.
- Check `spec/resources/media_entries/**`, `spec/resources/collection/**` and
  the audit specs for examples that rely on the bypass and update them.

## 2. `/media-entries/:id/media-files/data-stream/` denies anonymous on public full-size (1 failure)

Anonymous gets 401 on an entry with `get_metadata_and_previews` and
`get_full_size` set. v1 and v2's own `/media-files/:id/data-stream/` answer 200.

Where:

- `src/madek/api/resources/media_files.clj` (`media-entry-routes`) uses
  `jqh/ring-wrap-authorization-download`.
- That calls `authorize-request-for-media-resource` with `:download`
  (`media_resource_helper.clj`), which only short-circuits on public for
  `:view`, never for `:download`.

Suggested fix (either one):

- Replace `jqh/ring-wrap-add-media-resource` + `jqh/ring-wrap-authorization-download`
  with `media-files.authorization/wrap-auth-media-file-full_size`. The media
  file is already in the request via `wrap-find-and-add-media-file-by-media-entry-id`.
  This keeps both data-stream routes on the same code path.
- Or extend `authorize-request-for-media-resource` so `:download` passes when
  the resource has `get_full_size` (and `get_metadata_and_previews`).

## 3. `/groups/` and `/groups/:id` are public (4 failures)

v1 serves groups only to admins (`wrap-authorize-admin!`); v2 serves them to
anonymous and regular users.

Where:

- `src/madek/api/route_authorization.clj:56-57` lists both paths as `reference-data`.
- `src/madek/api/resources/groups.clj:224` `user-routes` has no admin guard
  (registered in `src/madek/api/resources.clj:194`).

Suggested fix:

- Remove the two entries from `route_authorization.clj` and add
  `wrap-authorize-admin!` to the `user-routes` handlers, or drop `user-routes`
  and rely on `/api-v2/admin/groups/`.
- Before doing so, decide whether a non-admin use case exists (e.g. the
  webapp picking a group when editing permissions). If it does, keep the
  routes authenticated-only and return a minimal schema, and change the
  spec instead of the code.
- Update `spec/resources/groups/index_spec.rb` (the "non admin-user" and
  "for user" contexts) and `spec/resources/audits/vocabulary_spec.rb:78-82`,
  which currently expect public access.

## 4. `/people/:id` exposes institutional fields (2 failures)

Anonymous and regular users additionally get `institutional_id`,
`institutional_directory_infos` and `institutional_directory_inactive_since`.
v1 restricts non-admins to `user-allowed-fields`: `id first_name last_name
pseudonym created_at updated_at subtype description external_uris
institution`.

Where:

- `src/madek/api/resources/people/common.clj:13-34`: `public-people-select-keys`
  only removes `admin_comment` and `identification_info`.
- `src/madek/api/resources/people/get.clj:85` uses `public-person-query`; the
  response schema around `get.clj:51` declares the institutional fields.

Suggested fix:

- Add `:people.institutional_id`, `:people.institutional_directory_infos`
  and `:people.institutional_directory_inactive_since` to `internal-people-keys`.
- Make the three keys optional (or remove them) in the public response
  schema so coercion does not fail.
- Admin routes keep using `person-query` with all fields.

## 5. Public `/delegations/:id` is missing (3 failures)

v1 serves `/delegations/:id` to everybody with only `id`, `name` and
`description`. v2 only has `/api-v2/admin/delegations/:id` and answers 404.

Where: `src/madek/api/resources/delegations.clj:149` (admin-only route).

Suggested fix:

- Add a user route `["/delegations/:id" {:get ...}]` that selects only
  `[:id :name :description]` and reuses the existing find/404 middleware.
- Register it with the other user routes in `src/madek/api/resources.clj`
  and add `[:get "/api-v2/delegations/:id"]` as public in
  `route_authorization.clj`.
- Note: `/api-v2/delegations/:delegation_id/` already exists in
  `delegations_users.clj:144` (authenticated, delegation users). Make sure
  the new path does not collide with it (trailing slash vs. none).

## Related bug, not an access divergence

`GET /media-entries/:id/meta-data/:meta_key_id/meta-data-people/:meta_datum_person_id`
answers 500. In `src/madek/api/resources/meta_data/get.clj:65`,
`handle_get-meta-datum-person` does `(:person_id meta-datum-person-id)` on
the path UUID, which is always nil.

Suggested fix: look up the `meta_data_people` row by
`meta_datum_person_id` (scoped to `md-id`), return 404 if missing, and
return that row (or join `people` on its `person_id`). Once fixed, the
meta-datum-person section in `media_resources_access_spec.rb` can also
test the base route instead of only `/person`.
