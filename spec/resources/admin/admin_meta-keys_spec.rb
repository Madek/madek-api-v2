require "spec_helper"
require "cgi"
require "timecop"

describe "Admin MetaKey Resource Access" do
  let(:vocabulary_id) { "copyright" }
  let(:metakey_id) { "#{vocabulary_id}:test_me_now" }

  let(:metakey_attrs) do
    {
      id: metakey_id,
      descriptions: {de: "Beschreibung", en: "Description"},
      meta_datum_object_type: "MetaDatum::TextDate",
      is_extensible_list: true,
      is_enabled_for_collections: true,
      allowed_rdf_class: "Keyword",
      documentation_urls: {de: "http://example.de/doc", en: "http://example.com/doc"},
      vocabulary_id: vocabulary_id,
      is_enabled_for_media_entries: true,
      position: 0,
      admin_comment: "Initial admin comment",
      labels: {de: "Label DE", en: "Label EN"},
      hints: {de: "Hinweis", en: "Hint"},
      keywords_alphabetical_order: true,
      text_type: "line"
    }
  end

  before :each do
    FactoryBot.create(:vocabulary, id: vocabulary_id)
  end

  context "Authenticated access to meta-key resources" do
    include_context :json_client_for_authenticated_token_admin

    it "performs full CRUD on meta-key resource" do
      # CREATE
      post_response = client.post("/api-v2/admin/meta-keys/") do |req|
        req.body = metakey_attrs.to_json
        req.headers["Content-Type"] = "application/json"
      end

      expect(post_response.status).to eq(200)
      created_id = post_response.body["id"]
      expect(created_id).to eq(metakey_id)

      # READ
      get_response = client.get("/api-v2/admin/meta-keys/#{metakey_id}")
      expect(get_response.status).to eq(200)
      expect(get_response.body["id"]).to eq(metakey_id)
      expect(get_response.body["labels"]["en"]).to eq("Label EN")

      # UPDATE
      updated_attrs = metakey_attrs.merge(admin_comment: "Updated admin comment")
      put_response = client.put("/api-v2/admin/meta-keys/#{metakey_id}") do |req|
        req.body = updated_attrs.to_json
        req.headers["Content-Type"] = "application/json"
      end

      expect(put_response.status).to eq(200)
      expect(put_response.body["admin_comment"]).to eq("Updated admin comment")

      # DELETE
      delete_response = client.delete("/api-v2/admin/meta-keys/#{metakey_id}")
      expect(delete_response.status).to eq(200)

      # VERIFY DELETION
      get_deleted = client.get("/api-v2/admin/meta-keys/#{metakey_id}")
      expect(get_deleted.status).to eq(404)
    end
  end
end
