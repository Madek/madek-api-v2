
Failures:

1) Access parity with the legacy API: public resources GET /people/:id exposes no more than the legacy fields to anonymous
   Failure/Error: expect(anonymous_client.get(path).body.keys - legacy_fields).to be_empty
   expected `["institutional_id", "institutional_directory_infos", "institutional_directory_inactive_since"].empty?` to be truthy, got false
   # ./spec/api/public_resources_access_spec.rb:40:in `block (3 levels) in <top (required)>'

2) Access parity with the legacy API: public resources GET /people/:id exposes no more than the legacy fields to a user
   Failure/Error: expect(client_for(FactoryBot.create(:user)).get(path).body.keys - legacy_fields).to be_empty
   expected `["institutional_id", "institutional_directory_infos", "institutional_directory_inactive_since"].empty?` to be truthy, got false
   # ./spec/api/public_resources_access_spec.rb:44:in `block (3 levels) in <top (required)>'

3) Access parity with the legacy API: public resources GET /delegations/:id grants access to anonymous
   Failure/Error: expect(response.status).to eq(expected)

   expected: 200
   got: 404

   (compared using ==)
   Shared Example Group: :legacy_public_access called from ./spec/api/public_resources_access_spec.rb:55
   # ./spec/api/shared.rb:164:in `expect_legacy_status'
   # ./spec/api/shared.rb:239:in `block (2 levels) in <top (required)>'

4) Access parity with the legacy API: public resources GET /delegations/:id grants access to a user
   Failure/Error: expect(response.status).to eq(expected)

   expected: 200
   got: 404

   (compared using ==)
   Shared Example Group: :legacy_public_access called from ./spec/api/public_resources_access_spec.rb:55
   # ./spec/api/shared.rb:164:in `expect_legacy_status'
   # ./spec/api/shared.rb:243:in `block (2 levels) in <top (required)>'

5) Access parity with the legacy API: public resources GET /delegations/:id exposes only id, name and description
   Failure/Error: expect(anonymous_client.get(path).body.keys).to match_array %w[id name description]

   NoMethodError:
   undefined method `keys' for an instance of String
   # ./spec/api/public_resources_access_spec.rb:58:in `block (3 levels) in <top (required)>'

Finished in 2.53 seconds (files took 1.48 seconds to load)
15 examples, 5 failures

Failed examples:

rspec ./spec/api/public_resources_access_spec.rb:39 # Access parity with the legacy API: public resources GET /people/:id exposes no more than the legacy fields to anonymous
rspec ./spec/api/public_resources_access_spec.rb:43 # Access parity with the legacy API: public resources GET /people/:id exposes no more than the legacy fields to a user
rspec ./spec/api/public_resources_access_spec.rb[1:5:1] # Access parity with the legacy API: public resources GET /delegations/:id grants access to anonymous
rspec ./spec/api/public_resources_access_spec.rb[1:5:2] # Access parity with the legacy API: public resources GET /delegations/:id grants access to a user
rspec ./spec/api/public_resources_access_spec.rb:57 # Access parity with the legacy API: public resources GET /delegations/:id exposes only id, name and description


--------

    responds 404 for a collection
SPEC_SEED a24a4ed4f09d9d8a4917c2d7508dba37df7abd88 set env SPEC_SEED to force value

Failures:

1) Access parity with the legacy API: media-resources media-entry with a media-file and a preview GET /media-entries/:id/media-files/data-stream when the resource is public grants access to anonymous
   Failure/Error: expect(response.status).to eq(expected)

   expected: 200
   got: 401

   (compared using ==)
   Shared Example Group: :legacy_media_resource_access called from ./spec/api/media_resources_access_spec.rb:82
   # ./spec/api/shared.rb:164:in `expect_legacy_status'
   # ./spec/api/shared.rb:191:in `block (4 levels) in <top (required)>'

Finished in 44.87 seconds (files took 1.63 seconds to load)
268 examples, 1 failure

Failed examples:

rspec ./spec/api/media_resources_access_spec.rb[1:1:5:3:1:1] # Ac


______


SPEC_SEED a24a4ed4f09d9d8a4917c2d7508dba37df7abd88 set env SPEC_SEED to force value

Failures:

1) Access parity with the legacy API: admin-only resources GET /groups/ denies anonymous
   Failure/Error: expect(response.status).to be_in(DENIED)
   expected `200.in?([401, 403])` to be truthy, got false
   Shared Example Group: :legacy_admin_only_access called from ./spec/api/admin_only_access_spec.rb:26
   # ./spec/api/shared.rb:162:in `expect_legacy_status'
   # ./spec/api/shared.rb:224:in `block (2 levels) in <top (required)>'

2) Access parity with the legacy API: admin-only resources GET /groups/ responds 403 to a user
   Failure/Error: expect(response.status).to eq(expected)

   expected: 403
   got: 200

   (compared using ==)
   Shared Example Group: :legacy_admin_only_access called from ./spec/api/admin_only_access_spec.rb:26
   # ./spec/api/shared.rb:164:in `expect_legacy_status'
   # ./spec/api/shared.rb:228:in `block (2 levels) in <top (required)>'

3) Access parity with the legacy API: admin-only resources GET /groups/:id denies anonymous
   Failure/Error: expect(response.status).to be_in(DENIED)
   expected `200.in?([401, 403])` to be truthy, got false
   Shared Example Group: :legacy_admin_only_access called from ./spec/api/admin_only_access_spec.rb:26
   # ./spec/api/shared.rb:162:in `expect_legacy_status'
   # ./spec/api/shared.rb:224:in `block (2 levels) in <top (required)>'

4) Access parity with the legacy API: admin-only resources GET /groups/:id responds 403 to a user
   Failure/Error: expect(response.status).to eq(expected)

   expected: 403
   got: 200

   (compared using ==)
   Shared Example Group: :legacy_admin_only_access called from ./spec/api/admin_only_access_spec.rb:26
   # ./spec/api/shared.rb:164:in `expect_legacy_status'
   # ./spec/api/shared.rb:228:in `block (2 levels) in <top (required)>'

Finished in 3.6 seconds (files took 1.59 seconds to load)
24 examples, 4 failures

Failed examples:

rspec ./spec/api/admin_only_access_spec.rb[1:1:1] # Access parity with the legacy API: admin-only resources GET /groups/ denies anonymous
rspec ./spec/api/admin_only_access_spec.rb[1:1:2] # Access parity with the legacy API: admin-only resources GET /groups/ responds 403 to a user
rspec ./spec/api/admin_only_access_spec.rb[1:2:1] # Access parity with the legacy API: admin-only resources GET /groups/:id denies anonymous
rspec ./spec/api/admin_only_access_spec.rb[1:2:2] # Access parity with the legacy API: admin-on