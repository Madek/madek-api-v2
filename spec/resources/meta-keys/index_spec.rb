require 'spec_helper'

describe 'index' do
  include_context :json_client_for_authenticated_user do
    let :meta_keys_resource do
      client.get('/api/meta-keys/')
    end

    it 'should return 200 with only viewable by public meta-keys' do
      vocab = FactoryBot.create(:vocabulary, enabled_for_public_view: false)
      meta_key = FactoryBot.create(:meta_key,
                                    id: "#{vocab.id}:#{Faker::Lorem.word}",
                                    vocabulary: vocab)
      expect(meta_keys_resource.status).to be == 200
      expect(meta_keys_resource.body['meta-keys'].count).to be == 8
    end

    context 'when user is authenticated' do
      context 'when view permission is true' do
        it 'returns meta key in collection through the user permissions' do
          vocabulary = FactoryBot.create(:vocabulary,
                                          enabled_for_public_view: false)
          meta_key = FactoryBot.create(:meta_key,
                                        id: "#{vocabulary.id}:#{Faker::Lorem.word}",
                                        vocabulary: vocabulary)
          Permissions::VocabularyUserPermission.create!(user_id: user.id,
                                                        view: true,
                                                        vocabulary: vocabulary)
          data = meta_keys_resource.body['meta-keys'][8] #.first
          

          expect(data).to have_key 'id'
          expect(data['id']).to eq meta_key.id
        end

        it 'returns meta key in collection through the group permissions' do
          vocabulary = FactoryBot.create(:vocabulary,
                                          enabled_for_public_view: false)
          meta_key = FactoryBot.create(:meta_key,
                                        id: "#{vocabulary.id}:#{Faker::Lorem.word}",
                                        vocabulary: vocabulary)
          group = FactoryBot.create :group
          group.users << user
          Permissions::VocabularyGroupPermission.create!(group_id: group.id,
                                                         view: true,
                                                         vocabulary: vocabulary)

          data = meta_keys_resource.body['meta-keys'][8] #.first

          expect(data).to have_key 'id'
          expect(data['id']).to eq meta_key.id
        end
      end

      context 'when view permission is false' do
        it 'does not return meta key through the user permissions' do
          vocabulary = FactoryBot.create(:vocabulary,
                                          enabled_for_public_view: false)
          meta_key = FactoryBot.create(:meta_key,
                                        id: "#{vocabulary.id}:#{Faker::Lorem.word}",
                                        vocabulary: vocabulary)
          Permissions::VocabularyUserPermission.create!(user_id: user.id,
                                                        view: false,
                                                        vocabulary: vocabulary)

          data = meta_keys_resource.body['meta-keys']

          expect(data.count).to be == 8 #be_zero
        end

        it 'does not return meta key through the group permissions' do
          vocabulary = FactoryBot.create(:vocabulary,
                                          enabled_for_public_view: false)
          meta_key = FactoryBot.create(:meta_key,
                                        id: "#{vocabulary.id}:#{Faker::Lorem.word}",
                                        vocabulary: vocabulary)
          group = FactoryBot.create :group
          group.users << user
          vgp = Permissions::VocabularyGroupPermission.create!(group_id: group.id,
                                                               view: false,
                                                               vocabulary: vocabulary)

          data = meta_keys_resource.body['meta-keys']

          expect(data.count).to be == 8 #be_zero
        end
      end
    end
  end
end
