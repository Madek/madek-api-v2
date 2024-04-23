require "spec_helper"

context "vocabulary permission" do
  include_context :json_client_for_authenticated_admin_user do
    def json_vocabulary_resource(vocabulary_id, is_authenticated_user = false)
      if is_authenticated_user
        client.get("#{api_base_url}/admin/vocabularies/#{vocabulary_id}")
      else
        plain_faraday_json_client.get("#{api_base_url}/vocabularies/#{vocabulary_id}")
      end
    end

    it "should return 200 for an existing vocabulary" do
      vocab = FactoryBot.create(:vocabulary,
        enabled_for_public_view: true)
      expect(
        json_vocabulary_resource(vocab.id).status
      ).to be == 200
    end

    it "should return 404 for non-existing vocabulary" do
      expect(
        json_vocabulary_resource("bla").status
      ).to be == 404
    end

    describe "create vocabulary" do
      it "returns public vocabulary" do
        @labels = {de: "labelde", en: "labelen"}
        vocab_id = "test_vocab"
        @create_data = {id: vocab_id,
                        enabled_for_public_use: true,
                        enabled_for_public_view: true,
                        position: 20,
                        labels: @labels,
                        descriptions: @labels}
        create_response = client.post("#{api_base_url}/admin/vocabularies/") do |req|
          req.body = @create_data.to_json
          req.headers["Content-Type"] = "application/json"
        end
        expect(create_response.status).to be == 200

        data = json_vocabulary_resource(vocab_id).body

        expect(data).to have_key "id"
        expect(data["id"]).to eq vocab_id
      end

      it "does not return non-public vocabulary" do
        @labels = {de: "labelde", en: "labelen"}
        vocab_id = "test_vocab"
        @create_data = {id: vocab_id,
                        enabled_for_public_use: true,
                        enabled_for_public_view: false,
                        position: 20,
                        labels: @labels,
                        descriptions: @labels}
        create_response = client.post("#{api_base_url}/admin/vocabularies/") do |req|
          req.body = @create_data.to_json
          req.headers["Content-Type"] = "application/json"
        end
        expect(create_response.status).to be == 200

        data = json_vocabulary_resource(vocab_id).body

        expect(data).not_to have_key "id"
        expect(data["message"]).to eq "Vocabulary could not be found!"
      end

      context "when user is authenticated" do
        context "when view permission is true" do
          it "returns vocabulary through the user permissions" do
            vocabulary = FactoryBot.create(:vocabulary,
              enabled_for_public_view: false)

            # user = FactoryBot.create(:user)
            user_id = entity.id
            create_user_resp = client.post("#{api_base_url}/admin/vocabularies/#{vocabulary.id}/perms/user/#{user_id}") do |req|
              req.body = {
                use: true,
                view: true
              }.to_json
              req.headers["Content-Type"] = "application/json"
            end

            expect(create_user_resp.status).to be 200

            data = json_vocabulary_resource(vocabulary.id, true).body

            expect(data).to have_key "id"
            expect(data["id"]).to eq vocabulary.id
          end

          it "returns vocabulary through the group permissions" do
            vocabulary = FactoryBot.create(:vocabulary,
              enabled_for_public_view: false)
            group = FactoryBot.create :group
            group.users << entity

            create_group_resp = client.post("#{api_base_url}/admin/vocabularies/#{vocabulary.id}/perms/group/#{group.id}") do |req|
              req.body = {
                use: true,
                view: true
              }.to_json
              req.headers["Content-Type"] = "application/json"
            end

            expect(create_group_resp.status).to be 200
            # Permissions::VocabularyGroupPermission.create!(group_id: group.id,
            #                                               view: true,
            #                                               vocabulary: vocabulary)

            data = json_vocabulary_resource(vocabulary.id, true).body

            expect(data).to have_key "id"
            expect(data["id"]).to eq vocabulary.id
          end
        end

        context "when view permission is false" do
          it "does not return vocabulary through the user permissions" do
            vocabulary = FactoryBot.create(:vocabulary,
              enabled_for_public_view: false)
            user_id = entity.id
            create_user_resp = client.post("#{api_base_url}/admin/vocabularies/#{vocabulary.id}/perms/user/#{user_id}") do |req|
              req.body = {
                use: true,
                view: false
              }.to_json
              req.headers["Content-Type"] = "application/json"
            end

            expect(create_user_resp.status).to be 200
            data = json_vocabulary_resource(vocabulary.id, true).body

            expect(data).not_to have_key "id"
            expect(data["message"]).to eq "Vocabulary could not be found!"
          end

          it "does not return vocabulary through the group permissions" do
            vocabulary = FactoryBot.create(:vocabulary,
              enabled_for_public_view: false)
            group = FactoryBot.create :group
            group.users << entity

            create_group_resp = client.post("#{api_base_url}/admin/vocabularies/#{vocabulary.id}/perms/group/#{group.id}") do |req|
              req.body = {
                use: true,
                view: false
              }.to_json
              req.headers["Content-Type"] = "application/json"
            end

            expect(create_group_resp.status).to be 200
            data = json_vocabulary_resource(vocabulary.id, true).body

            expect(data).not_to have_key "id"
            expect(data["message"]).to eq "Vocabulary could not be found!"
          end
        end
        context "when view permission is true, but updated then" do
          it "returns vocabulary through user permission, but does not after update" do
            vocabulary = FactoryBot.create(:vocabulary,
              enabled_for_public_view: false)

            # user = FactoryBot.create(:user)
            user_id = entity.id
            client.post("#{api_base_url}/admin/vocabularies/#{vocabulary.id}/perms/user/#{user_id}") do |req|
              req.body = {
                use: true,
                view: true
              }.to_json
              req.headers["Content-Type"] = "application/json"
            end

            data = json_vocabulary_resource(vocabulary.id, true).body
            expect(data).to have_key "id"
            expect(data["id"]).to eq vocabulary.id

            client.put("#{api_base_url}/admin/vocabularies/#{vocabulary.id}/perms/user/#{user_id}") do |req|
              req.body = {
                use: true,
                view: false
              }.to_json
              req.headers["Content-Type"] = "application/json"
            end

            data2 = json_vocabulary_resource(vocabulary.id, true).body
            expect(data2).not_to have_key "id"
            expect(data2["message"]).to eq "Vocabulary could not be found!"
          end

          it "returns vocabulary through the group permissions, but does not after update" do
            vocabulary = FactoryBot.create(:vocabulary,
              enabled_for_public_view: false)
            group = FactoryBot.create :group
            group.users << entity

            create_group_resp = client.post("#{api_base_url}/admin/vocabularies/#{vocabulary.id}/perms/group/#{group.id}") do |req|
              req.body = {
                use: true,
                view: true
              }.to_json
              req.headers["Content-Type"] = "application/json"
            end
            expect(create_group_resp).not_to be_nil

            data = json_vocabulary_resource(vocabulary.id, true).body
            expect(data).to have_key "id"
            expect(data["id"]).to eq vocabulary.id

            create_group_resp = client.put("#{api_base_url}/admin/vocabularies/#{vocabulary.id}/perms/group/#{group.id}") do |req|
              req.body = {
                use: true,
                view: false
              }.to_json
              req.headers["Content-Type"] = "application/json"
            end
            expect(create_group_resp).not_to be_nil

            data = json_vocabulary_resource(vocabulary.id, true).body

            expect(data).not_to have_key "id"
            expect(data["message"]).to eq "Vocabulary could not be found!"
          end
        end

        context "when view permission is true, but deleted then" do
          it "returns vocabulary through user permission, but does not after delete" do
            vocabulary = FactoryBot.create(:vocabulary,
              enabled_for_public_view: false)

            # user = FactoryBot.create(:user)
            user_id = entity.id
            client.post("#{api_base_url}/admin/vocabularies/#{vocabulary.id}/perms/user/#{user_id}") do |req|
              req.body = {
                use: true,
                view: true
              }.to_json
              req.headers["Content-Type"] = "application/json"
            end

            data = json_vocabulary_resource(vocabulary.id, true).body
            expect(data).to have_key "id"
            expect(data["id"]).to eq vocabulary.id

            client.delete("#{api_base_url}/admin/vocabularies/#{vocabulary.id}/perms/user/#{user_id}")

            data2 = json_vocabulary_resource(vocabulary.id, true).body
            expect(data2).not_to have_key "id"
            expect(data2["message"]).to eq "Vocabulary could not be found!"
          end

          it "returns vocabulary through the group permissions, but does not after delete" do
            vocabulary = FactoryBot.create(:vocabulary,
              enabled_for_public_view: false)
            group = FactoryBot.create :group
            group.users << entity

            create_group_resp = client.post("#{api_base_url}/admin/vocabularies/#{vocabulary.id}/perms/group/#{group.id}") do |req|
              req.body = {
                use: true,
                view: true
              }.to_json
              req.headers["Content-Type"] = "application/json"
            end
            expect(create_group_resp).not_to be_nil

            data = json_vocabulary_resource(vocabulary.id, true).body
            expect(data).to have_key "id"
            expect(data["id"]).to eq vocabulary.id

            create_group_resp = client.delete("#{api_base_url}/admin/vocabularies/#{vocabulary.id}/perms/group/#{group.id}")
            expect(create_group_resp).not_to be_nil
            data = json_vocabulary_resource(vocabulary.id, true).body

            expect(data).not_to have_key "id"
            expect(data["message"]).to eq "Vocabulary could not be found!"
          end
        end

        context "when view permission is false" do
          it "does not return vocabulary through the user permissions" do
            vocabulary = FactoryBot.create(:vocabulary,
              enabled_for_public_view: false)
            user_id = entity.id
            create_user_resp = client.post("#{api_base_url}/admin/vocabularies/#{vocabulary.id}/perms/user/#{user_id}") do |req|
              req.body = {
                use: true,
                view: false
              }.to_json
              req.headers["Content-Type"] = "application/json"
            end
            expect(create_user_resp).not_to be_nil

            data = json_vocabulary_resource(vocabulary.id, true).body
            expect(data).not_to have_key "id"
            expect(data["message"]).to eq "Vocabulary could not be found!"
          end

          it "does not return vocabulary through the group permissions" do
            vocabulary = FactoryBot.create(:vocabulary,
              enabled_for_public_view: false)
            group = FactoryBot.create :group
            group.users << entity

            create_group_resp = client.post("#{api_base_url}/admin/vocabularies/#{vocabulary.id}/perms/group/#{group.id}") do |req|
              req.body = {
                use: true,
                view: false
              }.to_json
              req.headers["Content-Type"] = "application/json"
            end
            expect(create_group_resp).not_to be_nil

            data = json_vocabulary_resource(vocabulary.id, true).body

            expect(data).not_to have_key "id"
            expect(data["message"]).to eq "Vocabulary could not be found!"
          end
        end
      end
    end
  end
end
