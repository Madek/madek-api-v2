require "spec_helper"
require Pathname(File.expand_path("..", __FILE__)).join("shared")

# Legacy relations: vocabularies, meta-keys, meta-key and the vocabulary
# dependent visibility of meta-data.
#
# Legacy rule (vocabularies/permissions.clj): a vocabulary is visible when it
# is enabled for public view or the user (directly or via a group) holds a
# `view` permission on it. Admins are not privileged.

describe "Access parity with the legacy API: vocabularies" do
  let(:vocabulary) { FactoryBot.create :vocabulary, enabled_for_public_view: false }
  let(:meta_key) { FactoryBot.create :meta_key_text, id: "#{vocabulary.id}:parity" }

  def vocabulary_client_under(condition)
    case condition
    when :anonymous
      anonymous_client
    when :unrelated_user
      client_for(FactoryBot.create(:user))
    when :admin_without_permission
      client_for(create_admin_user)
    when :user_permission, :user_permission_without_scope
      user = FactoryBot.create(:user)
      FactoryBot.create :vocabulary_user_permission, vocabulary: vocabulary, user: user,
        view: condition == :user_permission, use: true
      client_for(user)
    when :group_permission, :group_permission_without_scope
      user = FactoryBot.create(:user)
      group = FactoryBot.create(:group)
      group.users << user
      FactoryBot.create :vocabulary_group_permission, vocabulary: vocabulary, group: group,
        view: condition == :group_permission, use: true
      client_for(user)
    else
      raise ArgumentError, "unknown condition #{condition}"
    end
  end

  denying = %i[anonymous unrelated_user admin_without_permission
    user_permission_without_scope group_permission_without_scope]
  granting = %i[user_permission group_permission]

  context "when the vocabulary is enabled for public view" do
    before { vocabulary.update_columns(enabled_for_public_view: true) }

    describe "GET /vocabularies/:id" do
      let(:path) { "/api-v2/vocabularies/#{vocabulary.id}" }
      include_examples :legacy_public_access
    end

    describe "GET /meta-keys/:id" do
      let(:path) { "/api-v2/meta-keys/#{meta_key.id}" }
      include_examples :legacy_public_access
    end

    it "GET /vocabularies/ lists it for anonymous" do
      expect(anonymous_client.get("/api-v2/vocabularies/").body.to_json).to include vocabulary.id
    end

    it "GET /meta-keys/ lists its meta-keys for anonymous" do
      meta_key
      expect(anonymous_client.get("/api-v2/meta-keys/").body.to_json).to include meta_key.id
    end
  end

  context "when the vocabulary is not enabled for public view" do
    describe "GET /vocabularies/:id" do
      let(:path) { "/api-v2/vocabularies/#{vocabulary.id}" }

      denying.each do |condition|
        it "is not found for #{condition}" do
          expect(vocabulary_client_under(condition).get(path).status).to eq 404
        end
      end

      granting.each do |condition|
        it "grants access to #{condition}" do
          expect(vocabulary_client_under(condition).get(path).status).to eq ApiAccessParity::GRANTED
        end
      end
    end

    describe "GET /vocabularies/" do
      def listed?(condition)
        response = vocabulary_client_under(condition).get("/api-v2/vocabularies/")
        expect(response.status).to eq ApiAccessParity::GRANTED
        response.body["vocabularies"].map { |v| v["id"] }.include?(vocabulary.id)
      end

      denying.each do |condition|
        it "does not list it for #{condition}" do
          expect(listed?(condition)).to be false
        end
      end

      granting.each do |condition|
        it "lists it for #{condition}" do
          expect(listed?(condition)).to be true
        end
      end
    end

    describe "GET /meta-keys/" do
      def listed?(condition)
        meta_key
        response = vocabulary_client_under(condition).get("/api-v2/meta-keys/")
        expect(response.status).to eq ApiAccessParity::GRANTED
        response.body["meta-keys"].map { |mk| mk["id"] }.include?(meta_key.id)
      end

      denying.each do |condition|
        it "does not list its meta-keys for #{condition}" do
          expect(listed?(condition)).to be false
        end
      end

      granting.each do |condition|
        it "lists its meta-keys for #{condition}" do
          expect(listed?(condition)).to be true
        end
      end
    end

    # The legacy `/meta-keys/:id` ignores the vocabulary visibility (leak);
    # api-v2 has to grant at least the legitimate access.
    describe "GET /meta-keys/:id" do
      granting.each do |condition|
        it "grants access to #{condition}" do
          response = vocabulary_client_under(condition).get("/api-v2/meta-keys/#{meta_key.id}")
          expect(response.status).to eq ApiAccessParity::GRANTED
        end
      end
    end

    context "with a meta-datum of a public media-entry" do
      let(:media_entry) { FactoryBot.create :media_entry, get_metadata_and_previews: true }
      let!(:meta_datum) { FactoryBot.create :meta_datum_text, media_entry: media_entry, meta_key: meta_key }

      describe "GET /media-entries/:id/meta-data/" do
        def listed?(condition)
          response = vocabulary_client_under(condition).get("/api-v2/media-entries/#{media_entry.id}/meta-data/")
          expect(response.status).to eq ApiAccessParity::GRANTED
          response.body.to_json.include?(meta_datum.id)
        end

        denying.each do |condition|
          it "does not list it for #{condition}" do
            expect(listed?(condition)).to be false
          end
        end

        granting.each do |condition|
          it "lists it for #{condition}" do
            expect(listed?(condition)).to be true
          end
        end
      end

      # The legacy `/meta-data/:id` ignores the vocabulary visibility (leak);
      # api-v2 has to grant at least the legitimate access.
      describe "GET /meta-data/:id" do
        granting.each do |condition|
          it "grants access to #{condition}" do
            response = vocabulary_client_under(condition).get("/api-v2/meta-data/#{meta_datum.id}")
            expect(response.status).to eq ApiAccessParity::GRANTED
          end
        end
      end
    end
  end
end
