require "spec_helper"
require "shared/audit-validator"
require Pathname(File.expand_path("..", __FILE__)).join("shared")
require "cgi"
require "timecop"

shared_context :setup_post_data do
  let(:post_data) do
    {
      password_sign_in_enabled: true,
      email: "test@me.com",
      person_id: user.person.id,
      last_name: "string",
      first_name: "string",
      institution: "string",
      notes: "string",
      login: "string"
    }
  end
end

describe "Admin/User API with authentication" do
  include_context :setup_owner_user_for_token_access
  include_context :setup_post_data

  let!(:user_id) do
    response = wtoken_header_plain_faraday_json_client_post(user_token.token, "/api-v2/admin/users", body: post_data)
    expect(response.status).to eq(201)
    expect_audit_entries_count(1, 1, 1)
    remove_all_audits
    response.body["id"]
  end

  context "when updating a user" do
    it "audits the PATCH request" do
      response = wtoken_header_plain_faraday_json_client_patch(user_token.token, "/api-v2/admin/users/#{user_id}", body: {last_name: "new_last_name", first_name: "new_first_name"})
      expect(response.status).to eq(200)
      expect(response.body["last_name"]).to eq("new_last_name")
      expect(response.body["first_name"]).to eq("new_first_name")
      expect_audit_entries_count(1, 1, 1)
    end
  end

  context "when retrieving a user" do
    it "does not audit the GET request" do
      response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/admin/users/#{user_id}")
      expect(response.status).to eq(200)
      expect_audit_entries_count(0, 0, 0)
    end
  end

  context "when deleting a user" do
    it "audits the DELETE request" do
      response = wtoken_header_plain_faraday_json_client_delete(user_token.token, "/api-v2/admin/users/#{user_id}")
      expect(response.status).to eq(200)
      expect_audit_entries_count(1, 1, 1)
    end
  end

  context "when handling unauthorized requests" do
    it "audits the PATCH request with unauthorized access" do
      response = plain_faraday_json_client.patch("/api-v2/admin/users/#{user_id}") do |req|
        req.body = {last_name: "new_last_name", first_name: "new_first_name"}.to_json
        req.headers["Content-Type"] = "application/json"
      end
      expect(response.status).to eq(403)
      expect_audit_entries_count(1, 0, 1)
    end

    it "does not audit the GET request with unauthorized access" do
      response = plain_faraday_json_client.get("/api-v2/admin/users/#{user_id}")
      expect(response.status).to eq(403)
      expect_audit_entries_count(0, 0, 0)
    end

    it "audits the DELETE request with unauthorized access" do
      response = plain_faraday_json_client.delete("/api-v2/admin/users/#{user_id}")
      expect(response.status).to eq(403)
      expect_audit_entries_count(1, 0, 1)
    end

    it "audits the POST request with unauthorized access" do
      response = plain_faraday_json_client.post("/api-v2/admin/users") do |req|
        req.body = post_data.to_json
        req.headers["Content-Type"] = "application/json"
      end
      expect(response.status).to eq(403)
      expect_audit_entries_count(1, 0, 1)
    end
  end
end

describe "Admin/User API without authentication" do
  include_context :setup_owner_user_for_token_access
  include_context :setup_post_data

  context "when attempting to create a user without authentication" do
    it "audits the POST request but does not create the user" do
      response = plain_faraday_json_client.post("/api-v2/admin/users") do |req|
        req.body = post_data.to_json
        req.headers["Content-Type"] = "application/json"
      end
      expect(response.status).to eq(403)
      expect_audit_entries_count(1, 0, 1)
    end
  end
end
