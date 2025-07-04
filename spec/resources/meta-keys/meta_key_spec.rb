require "spec_helper"

describe "meta-key" do
  include_context :json_client_for_authenticated_token_user do
    def json_meta_key_resource(meta_key_id, params = {})
      query_params = URI.encode_www_form(params)
      query_params = "?" + query_params unless query_params.empty?
      plain_faraday_json_client.get("/api-v2/meta-keys/#{meta_key_id}#{query_params}")
    end

    it "should return 200 for an existing meta_key_id" do
      vocab = FactoryBot.create(:vocabulary,
        enabled_for_public_view: true)
      meta_key = FactoryBot.create(:meta_key,
        id: "#{vocab.id}:#{Faker::Lorem.word}",
        vocabulary: vocab)
      expect(
        json_meta_key_resource(meta_key.id).status
      ).to be == 200
    end

    it "should return 422 for malformatted meta_key_id" do
      [":bla", ":", "bla:", "bla"].each do |meta_key_id|
        expect(
          json_meta_key_resource(meta_key_id).status
        ).to be == 422
      end
    end

    it "should return 404 for non-existing meta_key_id" do
      expect(
        json_meta_key_resource("foo:bar").status
      ).to be == 404
    end

    describe "multilingual labels" do
      let(:vocabulary) { FactoryBot.create :vocabulary }
      let(:meta_key) do
        FactoryBot.create(
          :meta_key,
          id: "#{vocabulary.id}:#{Faker::Lorem.word}",
          vocabulary: vocabulary,
          labels: {
            de: "label de",
            en: "label en"
          }
        )
      end

      specify "result contains correct labels" do
        expect(json_meta_key_resource(meta_key.id).body["labels"])
          .to eq({"de" => "label de", "en" => "label en"})
      end

      #      specify 'result contains a label from default locale' do
      #        expect(
      #          json_meta_key_resource(meta_key.id).body['label']
      #        ).to eq 'label de'
      #      end
    end

    describe "multilingual descriptions" do
      let(:vocabulary) { FactoryBot.create :vocabulary }
      let(:meta_key) do
        FactoryBot.create(
          :meta_key,
          id: "#{vocabulary.id}:#{Faker::Lorem.word}",
          vocabulary: vocabulary,
          descriptions: {
            de: "description de",
            en: "description en"
          }
        )
      end

      specify "result contains correct descriptions" do
        expect(json_meta_key_resource(meta_key.id).body["descriptions"])
          .to eq({"de" => "description de", "en" => "description en"})
      end

      # specify 'result contains a description from default locale' do
      #  expect(
      #    json_meta_key_resource(meta_key.id).body['description']
      #  ).to eq 'description de'
      # end
    end

    describe "multilingual hints" do
      let(:vocabulary) { FactoryBot.create :vocabulary }
      let(:meta_key) do
        FactoryBot.create(
          :meta_key,
          id: "#{vocabulary.id}:#{Faker::Lorem.word}",
          vocabulary: vocabulary,
          hints: {
            de: "hint de",
            en: "hint en"
          }
        )
      end

      specify "result contains correct hints" do
        expect(
          json_meta_key_resource(meta_key.id).body["hints"]
        )
          .to eq({"de" => "hint de", "en" => "hint en"})
      end

      # specify 'result contains a hint from default locale' do
      #  expect(
      #    json_meta_key_resource(meta_key.id).body['hint']
      #  ).to eq 'hint de'
      # end
    end

    it "does not return admin_comment property" do
      vocabulary = FactoryBot.create(:vocabulary,
        enabled_for_public_view: true)
      meta_key = FactoryBot.create(:meta_key,
        id: "#{vocabulary.id}:#{Faker::Lorem.word}",
        vocabulary: vocabulary)

      expect(json_meta_key_resource(meta_key.id).body)
        .not_to have_key "admin_comment"
    end

    context "when meta key has some mappings" do
      it "returns io-mappings structure" do
        vocabulary = FactoryBot.create(:vocabulary,
          enabled_for_public_view: true)
        meta_key = FactoryBot.create(:meta_key,
          id: "#{vocabulary.id}:#{Faker::Lorem.word}",
          vocabulary: vocabulary)
        io_interface_1 = FactoryBot.create(:io_interface)
        io_interface_2 = FactoryBot.create(:io_interface)
        io_mapping_1 = FactoryBot.create(:io_mapping, io_interface: io_interface_1, meta_key: meta_key)
        io_mapping_2 = FactoryBot.create(:io_mapping, io_interface: io_interface_1, meta_key: meta_key)
        io_mapping_3 = FactoryBot.create(:io_mapping, io_interface: io_interface_2, meta_key: meta_key)
        io_mapping_4 = FactoryBot.create(:io_mapping, io_interface: io_interface_2, meta_key: meta_key)

        response_body = json_meta_key_resource(meta_key.id).body
        expect(response_body["io_mappings"]).to contain_exactly(
          a_hash_including("id" => io_interface_1.id,
            "keys" => contain_exactly(
              {"key" => io_mapping_1.key_map},
              {"key" => io_mapping_2.key_map}
            )),
          a_hash_including("id" => io_interface_2.id,
            "keys" => contain_exactly(
              {"key" => io_mapping_3.key_map},
              {"key" => io_mapping_4.key_map}
            ))
        )
      end
    end

    context "when meta key has no mappings" do
      it "returns an empty io-mappings structure" do
        vocabulary = FactoryBot.create(:vocabulary,
          enabled_for_public_view: true)
        meta_key = FactoryBot.create(:meta_key,
          id: "#{vocabulary.id}:#{Faker::Lorem.word}",
          vocabulary: vocabulary)

        response_body = json_meta_key_resource(meta_key.id).body

        expect(response_body["io_mappings"]).to eq []
      end
    end
  end
end
