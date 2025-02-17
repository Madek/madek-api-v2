require "spec_helper"
require "shared/audit-validator"
require Pathname(File.expand_path("..", __FILE__)).join("shared")
require "cgi"
require "timecop"

shared_context :post_data do
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

describe "Modify admin/user with authentication (GET/POST/PATCH/DELETE)" do
  include_context :setup_owner_user_for_token_access
  include_context :post_data

  before(:each) do
    remove_all_audits
  end

  let!(:user_id) do
    response = wtoken_header_plain_faraday_json_client_post(user_token.token, "/api-v2/admin/users", body: post_data)
    expect(response.status).to eq(201)
    expect_audit_entries_count(1, 7, 1)

    remove_all_audits
    response.body["id"]
  end

  context "when updating a user" do
    it "audits the PATCH request" do
      response = wtoken_header_plain_faraday_json_client_patch(user_token.token, "/api-v2/admin/users/#{user_id}",
        body: {last_name: "new_last_name", first_name: "new_first_name"})

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

  #########################################
  # audits request/response only for not authenticated user
  #########################################

  context "no audits for fetch OR aborted requests" do
    it "audits request/response of PATCH only" do
      response = plain_faraday_json_client.patch("/api-v2/admin/users/#{user_id}") do |req|
        req.body = {last_name: "new_last_name", first_name: "new_first_name"}.to_json
        req.headers["Content-Type"] = "application/json"
      end

      expect(response.status).to eq(403)
      expect_audit_entries_count(1, 0, 1)
    end

    it "audits nothing when GET" do
      response = plain_faraday_json_client.get("/api-v2/admin/users/#{user_id}")

      expect(response.status).to eq(403)
      expect_audit_entries_count(0, 0, 0)
    end

    it "audits request/response of DELETE only" do
      response = plain_faraday_json_client.delete("/api-v2/admin/users/#{user_id}")

      expect(response.status).to eq(403)
      expect_audit_entries_count(1, 0, 1)
    end

    it "audits request/response of POST only" do
      response = plain_faraday_json_client.post("/api-v2/admin/users") do |req|
        req.body = post_data.to_json
        req.headers["Content-Type"] = "application/json"
      end

      expect(response.status).to eq(403)
      expect_audit_entries_count(1, 0, 1)
    end
  end
end

describe "Blocks modification of admin/users without authentication (POST)" do
  include_context :setup_owner_user_for_token_access
  include_context :post_data

  before(:each) do
    remove_all_audits
  end

  context "when retrieving a user then 403, no creation of user" do
    it "does audit the POST request but required reference-entries as well they dont exist yet" do
      response = plain_faraday_json_client.post("/api-v2/admin/users") do |req|
        req.body = post_data.to_json
        req.headers["Content-Type"] = "application/json"
      end

      expect(response.status).to eq(403)
      expect_audit_entries_count(1, 5, 1)
      # HINT: as expected? request creates people/usage_terms/users/auth_systems_users/admins & audit entries if people/.. not exist
      # expect_audit_entries_count(1, 0, 1)
    end
  end
end
