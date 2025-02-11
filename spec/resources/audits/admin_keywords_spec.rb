require "spec_helper"
require "shared/audit-validator"
require Pathname(File.expand_path("..", __FILE__)).join("shared")
require "cgi"
require "timecop"

shared_context :post_data do
  let(:meta_key) do
    FactoryBot.create(:meta_key,
      id: "test:#{SecureRandom.uuid}",
      meta_datum_object_type: "MetaDatum::Text")
  end

  let(:post_data) do
    {
      meta_key_id: meta_key.id,
      term: "string",
      description: "string",
      position: 0,
      external_uris: ["string"]
    }
  end
end

describe "Modify admin/keywords with authentication (GET/POST/PUT/DELETE)" do
  include_context :setup_owner_user_for_token_access
  include_context :post_data

  before(:each) do
    remove_all_audits
  end

  let!(:user_id) do
    response = wtoken_header_plain_faraday_json_client_post(user_token.token, "/api-v2/admin/keywords", body: post_data)

    expect(response.status).to eq(200)
    expect_audit_entries_count(1, 9, 1)

    remove_all_audits
    response.body["id"]
  end

  context "when updating a keyword" do
    it "audits the PUT request" do
      response = wtoken_header_plain_faraday_json_client_put(user_token.token, "/api-v2/admin/keywords/#{user_id}",
        body: {
          description: "string2",
          position: 2
        })

      expect(response.status).to eq(200)
      expect(response.body["description"]).to eq("string2")
      expect(response.body["position"]).to eq(2)
      expect_audit_entries_count(1, 1, 1)
    end
  end

  context "when retrieving a keyword" do
    it "does not audit the GET request" do
      response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/admin/keywords/#{user_id}")

      expect(response.status).to eq(200)
      expect_audit_entries_count(0, 0, 0)
    end
  end

  context "when deleting a keyword" do
    it "audits the DELETE request" do
      response = wtoken_header_plain_faraday_json_client_delete(user_token.token, "/api-v2/admin/keywords/#{user_id}")

      expect(response.status).to eq(200)
      expect_audit_entries_count(1, 1, 1)
    end
  end

  #########################################
  # audits request/response only for not authenticated user
  #########################################

  context "no audits for fetch OR aborted requests" do
    it "audits request/response of PUT only" do
      response = plain_faraday_json_client.put("/api-v2/admin/keywords/#{user_id}") do |req|
        req.body = {
          description: "string2",
          position: 2
        }.to_json
        req.headers["Content-Type"] = "application/json"
      end

      expect(response.status).to eq(403)
      expect_audit_entries_count(1, 0, 1)
    end

    it "audits nothing when GET" do
      response = plain_faraday_json_client.get("/api-v2/admin/keywords/#{user_id}")

      expect(response.status).to eq(403)
      expect_audit_entries_count(0, 0, 0)
    end

    it "audits request/response of DELETE only" do
      response = plain_faraday_json_client.delete("/api-v2/admin/keywords/#{user_id}")

      expect(response.status).to eq(403)
      expect_audit_entries_count(1, 0, 1)
    end

    it "audits request/response of POST only" do
      response = plain_faraday_json_client.post("/api-v2/admin/keywords") do |req|
        req.body = post_data.to_json
        req.headers["Content-Type"] = "application/json"
      end

      expect(response.status).to eq(403)
      expect_audit_entries_count(1, 0, 1)
    end
  end
end

describe "Blocks modification of admin/keywords without authentication (POST)" do
  include_context :setup_owner_user_for_token_access
  include_context :post_data

  before(:each) do
    remove_all_audits
  end

  context "when retrieving a keyword then 403, no creation" do
    it "does audit the POST request but required reference-entries as well if they dont exist yet" do
      response = plain_faraday_json_client.post("/api-v2/admin/keywords") do |req|
        req.body = post_data.to_json
        req.headers["Content-Type"] = "application/json"
      end

      expect(response.status).to eq(403)
      expect_audit_entries_count(1, 2, 1)
      # HINT: as expected? request creates vocabulary/meta-keys & audit entries if voc/meta not exist
      # expect_audit_entries_count(1, 0, 1)
    end
  end
end
