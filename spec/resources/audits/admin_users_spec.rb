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

shared_context :setup_admin_user do
  let!(:new_admin_user_id) do
    remove_all_audits
    expect_audit_entries_count(0, 0, 0)
    response = wtoken_header_plain_faraday_json_client_post(user_token.token, "/api-v2/admin/users", body: post_data)
    expect(response.status).to eq(201)
    expect_audit_entries_count(1, 1, 1)
    response.body["id"]
  end

  before(:each) { remove_all_audits }
end

describe "Admin/User API with authentication with created user" do
  include_context :setup_owner_user_for_token_access
  include_context :setup_admin_user
  include_context :setup_post_data

  let(:user_id) do
    user.id
  end

  let(:owner_id) do
    owner.id
  end

  let(:admin_user_id) do
    admin_user.id
  end

  before(:each) do
    remove_all_audits
  end

  context "when updating a user" do
    it "audits the PATCH request" do
      expect_audit_entries_count(0, 0, 0)
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
      expect_audit_entries_count(0, 0, 0)
      response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/admin/users/#{user_id}")
      expect(response.status).to eq(200)
      expect_audit_entries_count(0, 0, 0)
    end

    it "verifies users response without pagination" do
      response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/admin/users")
      expect(response.status).to eq(200)
      expect(response.body["users"]).to be_a Array
    end

    it "verifies users response with pagination" do
      response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/admin/users?page=1&size=5")
      expect(response.status).to eq(200)
      expect(response.body["data"]).to be_a Array
      expect(response.body["pagination"]).to be_a Hash
    end
  end

  context "when deleting a user" do
    it "audits the DELETE request" do
      expect_audit_entries_count(0, 0, 0)
      response = wtoken_header_plain_faraday_json_client_delete(user_token.token, "/api-v2/admin/users/#{new_admin_user_id}")
      expect(response.status).to eq(200)
      expect_audit_entries_count(1, 1, 1)
    end

    it "audits the DELETE 200er request by user (with admin creds)" do
      expect_audit_entries_count(0, 0, 0)
      response = wtoken_header_plain_faraday_json_client_delete(user_token.token, "/api-v2/admin/users/#{new_admin_user_id}")
      expect(response.status).to eq(200)
      expect_audit_entries_count(1, 1, 1)
    end

    it "audits the DELETE 403er request by owner (without admin creds)" do
      expect_audit_entries_count(0, 0, 0)
      response = wtoken_header_plain_faraday_json_client_delete(owner_token.token, "/api-v2/admin/users/#{new_admin_user_id}")
      expect(response.status).to eq(403)
      expect_audit_entries_count(1, 0, 1)
    end

    it "audits the DELETE 200er request by admin_user" do
      expect_audit_entries_count(0, 0, 0)
      response = wtoken_header_plain_faraday_json_client_delete(admin_user_token.token, "/api-v2/admin/users/#{new_admin_user_id}")
      expect(response.status).to eq(200)
      expect_audit_entries_count(1, 1, 1)
    end

    it "audits the DELETE 422er request by admin_user" do
      expect_audit_entries_count(0, 0, 0)
      response = wtoken_header_plain_faraday_json_client_delete(admin_user_token.token, "/api-v2/admin/users/#{admin_user_id}")
      expect(response.status).to eq(422)
      expect(response.body["message"]).to eq("References still exist")
      expect_audit_entries_count(1, 0, 1)
    end
  end

  context "when handling requests as public user" do
    it "audits the PATCH request with unauthorized access" do
      expect_audit_entries_count(0, 0, 0)
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

describe "Admin/User API with authentication" do
  include_context :setup_owner_user_for_token_access
  include_context :setup_admin_user
  include_context :setup_post_data

  before(:each) do
    remove_all_audits
  end

  context "when updating a user" do
    it "audits the PATCH request" do
      expect_audit_entries_count(0, 0, 0)
      response = wtoken_header_plain_faraday_json_client_patch(user_token.token, "/api-v2/admin/users/#{new_admin_user_id}",
        body: {last_name: "new_last_name", first_name: "new_first_name"})
      expect(response.status).to eq(200)
      expect(response.body["last_name"]).to eq("new_last_name")
      expect(response.body["first_name"]).to eq("new_first_name")

      expect_audit_entries_count(1, 1, 1)
    end
  end

  context "when retrieving a user" do
    it "does not audit the GET request" do
      response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/admin/users/#{new_admin_user_id}")
      expect(response.status).to eq(200)
      expect_audit_entries_count(0, 0, 0)
    end
  end

  context "when deleting a user" do
    it "audits the DELETE request" do
      response = wtoken_header_plain_faraday_json_client_delete(user_token.token, "/api-v2/admin/users/#{new_admin_user_id}")
      expect(response.status).to eq(200)
      expect_audit_entries_count(1, 1, 1)
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
