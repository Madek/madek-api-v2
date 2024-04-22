require "spec_helper"

describe "index" do
  include_context :json_client_for_authenticated_user do
    let :vocabularies_resource do
      client.get("/api/vocabularies/")
    end

    it "should return 200 with only viewable by public vocabularies" do
      FactoryBot.create(:vocabulary, enabled_for_public_view: false)
      expect(vocabularies_resource.status).to be == 200
      # expect(vocabularies_resource.body['vocabularies'].count).to be == 1
      data = vocabularies_resource.body["vocabularies"]
      vocab_ids = ["madek_core"]
      data.each do |vocab|
        expect(vocab_ids).to include vocab["id"]
      end
    end

    context "when user is authenticated" do
      context "when view permission is true" do
        it "returns vocabulary in collection through the user permissions" do
          vocabulary = FactoryBot.create(:vocabulary,
            enabled_for_public_view: false)
          Permissions::VocabularyUserPermission.create!(user_id: user.id,
            view: true,
            vocabulary: vocabulary)

          # data = vocabularies_resource.body['vocabularies'].first
          # expect(data).to have_key 'id'
          # expect(data['id']).to eq vocabulary.id

          vocab_ids = [vocabulary.id, "madek_core"]
          data = vocabularies_resource.body["vocabularies"]
          data.each do |vocab|
            expect(vocab_ids).to include vocab["id"]
          end
        end

        it "returns vocabulary in collection through the group permissions" do
          vocabulary = FactoryBot.create(:vocabulary,
            enabled_for_public_view: false)
          group = FactoryBot.create :group
          group.users << user
          Permissions::VocabularyGroupPermission.create!(group_id: group.id,
            view: true,
            vocabulary: vocabulary)

          # data = vocabularies_resource.body['vocabularies'].first
          # expect(data).to have_key 'id'
          # expect(data['id']).to eq vocabulary.id

          data = vocabularies_resource.body["vocabularies"]
          vocab_ids = [vocabulary.id, "madek_core"]
          data.each do |vocab|
            expect(vocab_ids).to include vocab["id"]
          end
        end
      end

      context "when view permission is false" do
        it "does not return vocabulary through the user permissions" do
          vocabulary = FactoryBot.create(:vocabulary,
            enabled_for_public_view: false)
          Permissions::VocabularyUserPermission.create!(user_id: user.id,
            view: false,
            vocabulary: vocabulary)

          data = vocabularies_resource.body["vocabularies"]
          vocab_ids = ["madek_core"]
          data.each do |vocab|
            expect(vocab_ids).to include vocab["id"]
          end
          # expect(data.count).to be_zero
        end

        it "does not return vocabulary through the group permissions" do
          vocabulary = FactoryBot.create(:vocabulary,
            enabled_for_public_view: false)
          group = FactoryBot.create :group
          group.users << user
          Permissions::VocabularyGroupPermission.create!(group_id: group.id,
            view: false,
            vocabulary: vocabulary)

          data = vocabularies_resource.body["vocabularies"]
          vocab_ids = ["madek_core"]
          data.each do |vocab|
            expect(vocab_ids).to include vocab["id"]
          end
          # expect(data.count).to be_zero
        end
      end
    end
  end
end
