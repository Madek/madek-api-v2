require "spec_helper"
require "shared/audit-validator"
require Pathname(File.expand_path("..", __FILE__)).join("shared")
require "shared/clients"
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

  let!(:user_id) do
    response = wtoken_header_plain_faraday_json_client_post(user_token.token, "/api-v2/collections/", body: post_data)
    expect(response.status).to eq(200)
    response.body["id"]
  end

  before(:each) { remove_all_audits }

  context "when retrieving collection" do
    it "returns 401 for unauthorized GET request" do
      response = plain_faraday_json_client.get("/api-v2/workflows/")
      expect(response.status).to eq(401)
    end

    it "returns 200 for authorized GET request" do
      response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/workflows/")
      expect(response.status).to eq(200)

      response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/workflows/#{response.body.first["id"]}")
      expect(response.status).to eq(200)
    end

    it "handles POST, PUT, and DELETE requests correctly" do
      body = {
        name: "test",
        is_active: true,
        configuration: {foo: "bar"}
      }
      response = wtoken_header_plain_faraday_json_client_post(user_token.token, "/api-v2/workflows/", body: body)
      expect(response.status).to eq(200)
      id = response.body["id"]

      response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/workflows/#{id}")
      expect(response.status).to eq(200)

      body = {
        name: "test-updated",
        is_active: false,
        configuration: {karl: "heinz"}
      }
      response = wtoken_header_plain_faraday_json_client_put(user_token.token, "/api-v2/workflows/#{id}", body: body)
      expect(response.status).to eq(200)

      response = wtoken_header_plain_faraday_json_client_delete(user_token.token, "/api-v2/workflows/#{id}")
      expect(response.status).to eq(200)
    end
  end

  context "when handling unauthorized requests" do
    it "returns 401 for unauthorized GET request" do
      response = plain_faraday_json_client.get("/api-v2/workflows/")
      expect(response.status).to eq(401)
      expect_audit_entries_count(0, 0, 0)
    end

    it "returns 401 for unauthorized GET request with workflow ID" do
      response = plain_faraday_json_client.get("/api-v2/workflows/#{workflow.id}")
      expect(response.status).to eq(401)
    end

    it "returns 401 for unauthorized POST and PUT requests with invalid csrf" do
      body = {
        name: "test-updated",
        is_active: false,
        configuration: {karl: "heinz"}
      }

      response = plain_faraday_json_client.post("/api-v2/workflows/") do |req|
        req.body = body.to_json
        req.headers["Content-Type"] = "application/json"
      end
      expect(response.status).to eq(403)
      expect(response.body["msg"]).to eq("The anti-csrf-token cookie value is not set.")

      response = plain_faraday_json_client.put("/api-v2/workflows/#{workflow.id}") do |req|
        req.body = body.to_json
        req.headers["Content-Type"] = "application/json"
      end
      expect(response.status).to eq(403)
      expect(response.body["msg"]).to eq("The anti-csrf-token cookie value is not set.")
    end

    context "when handling unauthorized requests with valid csrf" do
      include_context :valid_session_with_csrf

      it "returns 401 for unauthorized POST and PUT requests" do
        body = {
          name: "test-updated",
          is_active: false,
          configuration: {karl: "heinz"}
        }

        response = client.post("/api-v2/workflows/") do |req|
          req.body = body.to_json
          req.headers["Content-Type"] = "application/json"
        end
        expect(response.status).to eq(200)

        response = client.put("/api-v2/workflows/#{workflow.id}") do |req|
          req.body = body.to_json
          req.headers["Content-Type"] = "application/json"
        end

        expect(response.status).to eq(200)
      end
    end

    it "returns 403 for unauthorized DELETE request" do
      response = plain_faraday_json_client.delete("/api-v2/workflows/#{workflow.id}")
      expect(response.status).to eq(403)
      expect(response.body["msg"]).to eq("The anti-csrf-token cookie value is not set.")
    end
  end
end
