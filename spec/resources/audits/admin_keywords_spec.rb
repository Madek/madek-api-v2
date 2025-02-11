require "spec_helper"
require "shared/audit-validator"
require Pathname(File.expand_path("..", __FILE__)).join("shared")
require "cgi"
require "timecop"

shared_context :setup_post_data do
  let!(:meta_key) do
    FactoryBot.create(:meta_key, id: "test:#{SecureRandom.uuid}", meta_datum_object_type: "MetaDatum::Text")
  end

  let!(:post_data) do
    {
      meta_key_id: meta_key.id,
      term: "string",
      description: "string",
      position: 0,
      external_uris: ["string"]
    }
  end

  before(:each) { remove_all_audits }
end

describe "Admin Keywords API with authentication" do
  include_context :setup_owner_user_for_token_access
  include_context :setup_post_data

  let!(:user_id) do
    response = wtoken_header_plain_faraday_json_client_post(user_token.token, "/api-v2/admin/keywords", body: post_data)
    expect(response.status).to eq(200)
    expect_audit_entries_count(1, 1, 1)
    response.body["id"]
  end

  before(:each) { remove_all_audits }

  context "when updating a keyword" do
    it "audits the PUT request" do
      response = wtoken_header_plain_faraday_json_client_put(user_token.token, "/api-v2/admin/keywords/#{user_id}", body: {description: "string2", position: 2})
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

  context "when handling unauthorized requests" do
    it "audits the PUT request with unauthorized access" do
      response = plain_faraday_json_client.put("/api-v2/admin/keywords/#{user_id}") do |req|
        req.body = {description: "string2", position: 2}.to_json
        req.headers["Content-Type"] = "application/json"
      end
      expect(response.status).to eq(403)
      expect_audit_entries_count(1, 0, 1)
    end

    it "does not audit the GET request with unauthorized access" do
      response = plain_faraday_json_client.get("/api-v2/admin/keywords/#{user_id}")
      expect(response.status).to eq(403)
      expect_audit_entries_count(0, 0, 0)
    end

    it "audits the DELETE request with unauthorized access" do
      response = plain_faraday_json_client.delete("/api-v2/admin/keywords/#{user_id}")
      expect(response.status).to eq(403)
      expect_audit_entries_count(1, 0, 1)
    end

    it "audits the POST request with unauthorized access" do
      response = plain_faraday_json_client.post("/api-v2/admin/keywords") do |req|
        req.body = post_data.to_json
        req.headers["Content-Type"] = "application/json"
      end
      expect(response.status).to eq(403)
      expect_audit_entries_count(1, 0, 1)
    end
  end
end

describe "Admin Keywords API without authentication" do
  include_context :setup_owner_user_for_token_access
  include_context :setup_post_data

  context "when attempting to create a keyword without authentication" do
    it "audits the POST request but does not create the keyword" do
      response = plain_faraday_json_client.post("/api-v2/admin/keywords") do |req|
        req.body = post_data.to_json
        req.headers["Content-Type"] = "application/json"
      end
      expect(response.status).to eq(403)
      expect_audit_entries_count(1, 0, 1)
    end
  end
end
