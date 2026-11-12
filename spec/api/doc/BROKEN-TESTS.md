master'.
ruby 3.3.8 is already installed
ruby 3.3.8 is already installed

a bunch of media entries with different properties
SPEC_SEED 000a8f43e66180ce8a3c2883c453afc2cabdb068 set env SPEC_SEED to force value
JSON `client` for authenticated `user`
the media_entries resource
Data from '/ramdisk/ci-working-dir/58651700-4a32-4a4a-a370-c7baf6feda6c/datalayer/db/seeds.pgbin' has been restored to                         'madek_api_58651700-4a32-4a4a-a370-c7baf6feda6c'
delete media_entries (FAILED - 1)
Data from '/ramdisk/ci-working-dir/58651700-4a32-4a4a-a370-c7baf6feda6c/datalayer/db/seeds.pgbin' has been restored to                         'madek_api_58651700-4a32-4a4a-a370-c7baf6feda6c'
delete media_entries
Data from '/ramdisk/ci-working-dir/58651700-4a32-4a4a-a370-c7baf6feda6c/datalayer/db/seeds.pgbin' has been restored to                         'madek_api_58651700-4a32-4a4a-a370-c7baf6feda6c'
checks conf-links (FAILED - 2)
Data from '/ramdisk/ci-working-dir/58651700-4a32-4a4a-a370-c7baf6feda6c/datalayer/db/seeds.pgbin' has been restored to                         'madek_api_58651700-4a32-4a4a-a370-c7baf6feda6c'
checks media-file (FAILED - 3)
Data from '/ramdisk/ci-working-dir/58651700-4a32-4a4a-a370-c7baf6feda6c/datalayer/db/seeds.pgbin' has been restored to                         'madek_api_58651700-4a32-4a4a-a370-c7baf6feda6c'
checks meta-datum (FAILED - 4)
SPEC_SEED 000a8f43e66180ce8a3c2883c453afc2cabdb068 set env SPEC_SEED to force value

Failures:

1) a bunch of media entries with different properties JSON `client` for authenticated `user` the media_entries resource delete media_entries
   Failure/Error: expect(response.status).to be == 200

   expected: == 200
   got:    403
   Shared Example Group: :json_client_for_authenticated_token_admin called from ./spec/resources/media_entries/soft-delete/without_soft_delete_spec.rb:8
   # ./spec/resources/media_entries/soft-delete/without_soft_delete_spec.rb:23:in `block (6 levels) in <top (required)>'
   # ./spec/resources/media_entries/soft-delete/without_soft_delete_spec.rb:21:in `each'
   # ./spec/resources/media_entries/soft-delete/without_soft_delete_spec.rb:21:in `block (5 levels) in <top (required)>'

2) a bunch of media entries with different properties JSON `client` for authenticated `user` the media_entries resource checks conf-links
   Failure/Error: expect(response.status).to be == 200

   expected: == 200
   got:    403
   Shared Example Group: :json_client_for_authenticated_token_admin called from ./spec/resources/media_entries/soft-delete/without_soft_delete_spec.rb:8
   # ./spec/resources/media_entries/soft-delete/without_soft_delete_spec.rb:89:in `block (5 levels) in <top (required)>'

3) a bunch of media entries with different properties JSON `client` for authenticated `user` the media_entries resource checks media-file
   Failure/Error: expect(response.status).to be == 200

   expected: == 200
   got:    403
   Shared Example Group: :json_client_for_authenticated_token_admin called from ./spec/resources/media_entries/soft-delete/without_soft_delete_spec.rb:8
   # ./spec/resources/media_entries/soft-delete/without_soft_delete_spec.rb:139:in `block (5 levels) in <top (required)>'

4) a bunch of media entries with different properties JSON `client` for authenticated `user` the media_entries resource checks meta-datum
   Failure/Error: expect(response.status).to be == 200

   expected: == 200
   got:    404
   Shared Example Group: :json_client_for_authenticated_token_admin called from ./spec/resources/media_entries/soft-delete/without_soft_delete_spec.rb:8
   # ./spec/resources/media_entries/soft-delete/without_soft_delete_spec.rb:175:in `block (5 levels) in <top (required)>'

Finished in 6.61 seconds (files took 1.83 seconds to load)
5 examples, 4 failures

Failed examples:

rspec ./spec/resources/media_entries/soft-delete/without_soft_delete_spec.rb:14 # a bunch of media entries with different properties JSON `client` for authenticated `user` the media_entries resource delete media_entries
rspec ./spec/resources/media_entries/soft-delete/without_soft_delete_spec.rb:78 # a bunch of media entries with different properties JSON `client` for authenticated `user` the media_entries resource checks conf-links
rspec ./spec/resources/media_entries/soft-delete/without_soft_delete_spec.rb:123 # a bunch of media entries with different properties JSON `client` for authenticated `user` the media_entries resource checks media-file
rspec ./spec/resources/media_entries/soft-delete/without_soft_delete_spec.rb:142 # a bunch of media entries with different properties JSON `client` for authenticated `user` the media_entries resource checks meta-datum
