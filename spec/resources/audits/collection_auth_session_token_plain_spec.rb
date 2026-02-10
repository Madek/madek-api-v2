require "spec_helper"
require "shared/audit-validator"
require Pathname(File.expand_path("..", __FILE__)).join("shared")
require Pathname(File.expand_path("../../..", __FILE__)).join("shared/clients")

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
      sorting: "created_at DESC",
      default_context_id: nil,
      default_resource_type: "all"
    }
  end

  let!(:context) do
    FactoryBot.create(:context, id: "columns")
  end

  let!(:post_data) do
    {
      default_resource_type: "collections",
      get_metadata_and_previews: true,
      default_context_id: context.id,
      layout: "list",
      sorting: "manual DESC",
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
    response = wtoken_header_plain_faraday_json_client_post(user_token.token, "/api-v2/collections/", body: post_data)
    expect(response.status).to eq(200)
    expect_audit_entries_count(1, 1, 1)
    response.body["id"]
  end

  context "when retrieving collection" do
    ["/api-v2/collections/", "/api-v2/admin/collections/"].each do |path|
      it "returns 200 for GET #{path}" do
        response = wtoken_header_plain_faraday_json_client_get(user_token.token, path)
        expect(response.status).to eq(200)
      end
    end

    it "returns 200 for authorized GET /api-v2/auth-info/" do
      response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/auth-info/")
      expect(response.status).to eq(200)
    end

    it "returns 401 for unauthorized GET /api-v2/auth-info/" do
      response = plain_faraday_json_client.get("/api-v2/auth-info/")
      expect(response.status).to eq(401)
      expect(response.body["message"]).to eq("Not authorized")
    end
  end
end

describe "Fetch collections" do
  include_context :setup_owner_user_for_token_access
  include_context :setup_post_data

  let!(:collection_id) do
    response = wtoken_header_plain_faraday_json_client_post(admin_user_token.token, "/api-v2/collections/", body: post_data)
    expect(response.status).to eq(200)
    response.body["id"]
  end

  context "with admin_user by token" do
    ["/api-v2/collections/", "/api-v2/admin/collections/"].each do |path|
      it "returns 200 for GET #{path}" do
        response = wtoken_header_plain_faraday_json_client_get(admin_user_token.token, path)
        expect(response.status).to eq(200)
      end
    end
  end

  it "returns 200 for authorized GET /api-v2/auth-info/" do
    response = wtoken_header_plain_faraday_json_client_get(admin_user_token.token, "/api-v2/auth-info/")
    expect(response.status).to eq(200)
  end

  it "returns 401 for unauthorized GET /api-v2/auth-info/" do
    response = plain_faraday_json_client.get("/api-v2/auth-info/")
    expect(response.status).to eq(401)
    expect(response.body["message"]).to eq("Not authorized")
  end
end

describe "Fetch collections (session-based)" do
  include_context :valid_session_with_csrf
  include_context :setup_post_data

  let!(:collection_id) do
    response = client.post("/api-v2/collections/") do |req|
      req.body = post_data.to_json
      req.headers["Content-Type"] = "application/json"
    end
    expect(response.status).to eq(200)
    response.body["id"]
  end

  context "with admin_user by session" do
    it "returns 200 for GET /api-v2/collections/" do
      response = client.get("/api-v2/collections/")
      expect(response.status).to eq(200)
    end

    it "returns 403 for GET /api-v2/admin/collections/" do
      response = client.get("/api-v2/admin/collections/")
      expect(response.status).to eq(403)
      expect(response.body["msg"]).to eq("Only administrators are allowed to access this resource.")
    end
  end

  it "returns 200 for authorized GET /api-v2/auth-info/" do
    response = client.get("/api-v2/auth-info/")
    expect(response.status).to eq(200)
  end

  it "returns 200 for unauthorized GET /api-v2/auth-info/" do
    response = plain_faraday_json_client.get("/api-v2/auth-info/")
    expect(response.status).to eq(200)
  end
end

describe "Fetch collections (admin session)" do
  include_context :valid_admin_session_with_csrf
  include_context :setup_post_data

  let!(:collection_id) do
    response = client.post("/api-v2/collections/") do |req|
      req.body = post_data.to_json
      req.headers["Content-Type"] = "application/json"
    end
    expect(response.status).to eq(200)
    response.body["id"]
  end

  context "with admin_user session" do
    it "returns 200 for GET /api-v2/collections/" do
      response = client.get("/api-v2/collections/")
      expect(response.status).to eq(200)
    end

    it "returns 200 for GET /api-v2/admin/collections/" do
      response = client.get("/api-v2/admin/collections/")
      expect(response.status).to eq(200)
    end
  end

  it "returns 200 for authorized GET /api-v2/auth-info/" do
    response = client.get("/api-v2/auth-info/")
    expect(response.status).to eq(200)
  end

  it "returns 200 for unauthorized GET /api-v2/auth-info/" do
    response = plain_faraday_json_client.get("/api-v2/auth-info/")
    expect(response.status).to eq(200)
  end
end

describe "No access without session" do
  it "returns 401 for unauthorized GET /api-v2/auth-info/" do
    response = plain_faraday_json_client.get("/api-v2/auth-info/")
    expect(response.status).to eq(401)
    expect(response.body["message"]).to eq("Not authorized")
  end
end
