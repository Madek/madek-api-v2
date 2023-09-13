require 'spec_helper'
require Pathname(File.expand_path('../../', __FILE__)).join('shared')

describe 'filtering media entries' do
  include_context :bunch_of_media_entries

  def get_media_entries_response(filter = nil)
    #media_entries # force evaluation
    client.get("/api/media-entries", filter)
  end

  def get_media_entries(filter = nil)
    get_media_entries_response(filter).body['media_entries']
  end

  context 'permission params checks' do
    include_context :json_client_for_authenticated_user do
      it 'returns 422 if some \'me_\' not true' do
        response = get_media_entries_response('me_get_metadata_and_previews' => false)
        expect(response.status).to be == 422
      end
    end
  end

  context 'by public_ permissions' do
    include_context :json_client_for_authenticated_user do
      it 'public_get_metadata_and_previews' do
        get_media_entries('public_get_metadata_and_previews' => true)
          .each do |me|
          media_entry = MediaEntry.unscoped.find(me['id'])
          expect(media_entry.get_metadata_and_previews).to be true
        end
      end

      it 'public_get_full_size' do
        get_media_entries('public_get_full_size' => true)
          .each do |me|
          media_entry = MediaEntry.unscoped.find(me['id'])
          expect(media_entry.get_full_size).to be true
        end
      end
    end
  end

  context 'by me_ permissons' do
    #let :media_entries_relation do
    #  client.get.relation('media-entries')
    #end

    context 'me_get_metadata_and_previews for a user' do
      include_context :json_client_for_authenticated_user do
        it '200 for public permissions' do
          10.times {
            FactoryBot.create(:media_entry,
                               get_metadata_and_previews: true,
                               get_full_size: false)
          }

          get_media_entries('me_get_metadata_and_previews' => true)
            .each do |me|
            media_entry = MediaEntry.unscoped.find(me['id'])
            expect(media_entry.get_metadata_and_previews).to be true
          end
        end

        it '200 for responsible user' do
          10.times {
            FactoryBot.create(:media_entry,
                               responsible_user: user,
                               get_metadata_and_previews: false,
                               get_full_size: false)
          }

          get_media_entries('me_get_metadata_and_previews' => 'true')
            .each do |me|
            media_entry = MediaEntry.unscoped.find(me['id'])
            expect(media_entry.responsible_user.id).to be == user.id
          end
        end

        it '200 for user permission' do
          10.times do
            FactoryBot.create \
              :media_entry_user_permission,
              media_entry: FactoryBot.create(:media_entry,
                                              get_metadata_and_previews: false,
                                              get_full_size: false),
              user: user
          end

          get_media_entries('me_get_metadata_and_previews' => true)
            .each do |me|
            media_entry = MediaEntry.unscoped.find(me['id'])
            expect(media_entry.user_permissions.first.user).to be == user
          end
        end

        it '200 for group permission' do
          10.times do
            g = FactoryBot.create(:group)
            user.groups << g
            FactoryBot.create \
              :media_entry_group_permission,
              media_entry: FactoryBot.create(:media_entry,
                                              get_metadata_and_previews: false,
                                              get_full_size: false),
              group: g
          end

          get_media_entries('me_get_metadata_and_previews' => 'true')
            .each do |me|
            media_entry = MediaEntry.unscoped.find(me['id'])
            expect(user.groups)
              .to include media_entry.group_permissions.first.group
          end
        end
      end
    end

    context 'me_get_full_size for a user' do
      include_context :json_client_for_authenticated_user do
        it '200 for public permissions' do
          10.times {
            FactoryBot.create(:media_entry,
                               get_metadata_and_previews: false,
                               get_full_size: true)
          }

          get_media_entries('me_get_full_size' => 'true')
            .each do |me|
            media_entry = MediaEntry.unscoped.find(me['id'])
            expect(media_entry.get_full_size).to be true
          end
        end

        it '200 for responsible user' do
          10.times {
            FactoryBot.create(:media_entry,
                               responsible_user: user,
                               get_metadata_and_previews: false,
                               get_full_size: false)
          }

          get_media_entries('me_get_full_size' => 'true')
            .each do |me|
            media_entry = MediaEntry.unscoped.find(me['id'])
            expect(media_entry.responsible_user).to be == user
          end
        end

        it '200 for user permission' do
          10.times do
            FactoryBot.create \
              :media_entry_user_permission,
              media_entry: FactoryBot.create(:media_entry,
                                              get_metadata_and_previews: false,
                                              get_full_size: false),
              user: user
          end

          get_media_entries('me_get_full_size' => 'true')
            .each do |me|
            media_entry = MediaEntry.unscoped.find(me['id'])
            expect(media_entry.user_permissions.first.user).to be == user
          end
        end

        it '200 for group permission' do
          10.times do
            g = FactoryBot.create(:group)
            user.groups << g
            FactoryBot.create \
              :media_entry_group_permission,
              media_entry: FactoryBot.create(:media_entry,
                                              get_metadata_and_previews: false,
                                              get_full_size: false),
              group: g
          end

          get_media_entries('me_get_full_size' => 'true')
            .each do |me|
            media_entry = MediaEntry.unscoped.find(me['id'])
            expect(user.groups)
              .to include media_entry.group_permissions.first.group
          end
        end
      end
    end

    
  end
end
