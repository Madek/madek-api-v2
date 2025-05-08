require "spec_helper"
require "shared/audit-validator"
require Pathname(File.expand_path("..", __FILE__)).join("shared")
require "cgi"
require "timecop"

shared_context :setup_post_data do
  let!(:responsible_user) do
    p1 = FactoryBot.create(:person, institution: "local", institutional_id: nil)
    FactoryBot.create(:user, institution: "local", institutional_id: nil, person: p1)
  end

  let!(:put_data) do
    {
      layout: "list",
      is_master: true,
      sorting: "created_at DESC",
      default_context_id: nil,
      workflow_id: nil,
      default_resource_type: "all"
    }
  end

  let!(:workflow) do
    Workflow.create(name: Faker::Educator.course, creator: user)
  end

  let!(:context) do
    FactoryBot.create(:context, id: "columns")
  end

  let!(:post_data) do
    {
      default_resource_type: "collections",
      get_metadata_and_previews: true,
      is_master: true,
      default_context_id: context.id,
      layout: "list",
      sorting: "manual DESC",
      workflow_id: workflow.id,
      responsible_delegation_id: nil,
      responsible_user_id: user.id
    }
  end

  before(:each) { remove_all_audits }
end

describe "Modify collection with authentication (GET/POST/PUT/DELETE)" do
  include_context :setup_owner_user_for_token_access
  include_context :setup_post_data

  let!(:collection_id) do
    response = wtoken_header_plain_faraday_json_client_post(user_token.token, "/api-v2/collection", body: post_data)
    expect(response.status).to eq(200)
    expect_audit_entries_count(1, 1, 1)
    response.body["id"]
  end

  before(:each) { remove_all_audits }

  context "when updating collection" do
    it "audits the POST request" do
      response = wtoken_header_plain_faraday_json_client_post(user_token.token, "/api-v2/collection", body: post_data)
      expect(response.status).to eq(200)
      expect_audit_entries_count(1, 1, 1)
    end

    it "audits the PUT request" do
      response = wtoken_header_plain_faraday_json_client_put(user_token.token, "/api-v2/collection/#{collection_id}", body: put_data)
      expect(response.status).to eq(200)
      expect_audit_entries_count(1, 1, 1)
    end
  end

  context "when retrieving collection" do
    it "does not audit the GET request" do
      response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/collection/#{collection_id}")
      expect(response.status).to eq(200)
      expect_audit_entries_count(0, 0, 0)
    end
  end

  context "when deleting collection" do
    it "audits the DELETE request" do
      response = wtoken_header_plain_faraday_json_client_delete(user_token.token, "/api-v2/collection/#{collection_id}")
      expect(response.status).to eq(200)
      expect_audit_entries_count(1, 1, 1)
    end
  end

  context "when handling unauthorized requests" do
    it "audits the PUT request with unauthorized access" do
      response = plain_faraday_json_client_csrf.put("/api-v2/collection/#{collection_id}") do |req|
        req.body = put_data
        req.headers["Content-Type"] = "application/json"
      end
      expect(response.status).to eq(401)
      expect_audit_entries_count(1, 0, 1)
    end

    it "does not audit the GET request with unauthorized access" do
      response = plain_faraday_json_client_csrf.get("/api-v2/collection/#{collection_id}")
      expect(response.status).to eq(200)
      expect_audit_entries_count(0, 0, 0)
    end

    it "audits the DELETE request with unauthorized access" do
      response = plain_faraday_json_client_csrf.delete("/api-v2/collection/#{collection_id}")
      expect(response.status).to eq(401)
      expect_audit_entries_count(1, 0, 1)
    end

    it "audits the POST request with unauthorized access" do
      response = plain_faraday_json_client_csrf.post("/api-v2/collection") do |req|
        req.body = post_data
        req.headers["Content-Type"] = "application/json"
      end
      expect(response.status).to eq(401)
      expect_audit_entries_count(1, 0, 1)
    end
  end
end

describe "Blocks modification of collection without authentication (POST)" do
  include_context :setup_owner_user_for_token_access
  include_context :setup_post_data

  context "when attempting to create a collection without authentication" do
    it "audits the POST request but does not create the collection" do
      response = plain_faraday_json_client_csrf.post("/api-v2/collection") do |req|
        req.body = post_data
        req.headers["Content-Type"] = "application/json"
      end
      expect(response.status).to eq(401)
      expect_audit_entries_count(1, 0, 1)
    end
  end

  context "when attempting to create a collection without authentication" do
    it "audits the POST request but does not create the collection" do
      response = plain_faraday_json_client_csrf.post("/api-v2/collection") do |req|
        req.body = post_data
      end
      expect(response.status).to eq(401)
    end
  end
end
