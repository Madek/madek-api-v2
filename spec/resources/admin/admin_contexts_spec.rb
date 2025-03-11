require "spec_helper"
require "cgi"
require "timecop"

describe "Admin Context Resource Access" do
  before :each do
    @keywords = Array.new(10) { FactoryBot.create(:context_key) }
  end

  context "Unauthenticated access to contexts" do
    include_context :json_client_for_authenticated_token_user

    let(:client) { plain_faraday_json_client }

    describe "Fetching all contexts" do
      let(:plain_json_response) { client.get("/api-v2/admin/contexts") }

      it "returns 403 Unauthorized" do
        expect(plain_json_response.status).to eq(403)
        expect(plain_json_response.body["msg"]).to eq("Only administrators are allowed to access this resource.")
      end
    end

    describe "Fetching a specific contexts" do
      let(:plain_json_response) do
        client.get("/api-v2/admin/contexts/#{@keywords.first.context_id}")
      end

      it "returns 403 Unauthorized" do
        expect(plain_json_response.status).to eq(403)
        expect(plain_json_response.body["msg"]).to eq("Only administrators are allowed to access this resource.")
      end
    end
  end

  context "Authenticated access to protected contexts" do
    include_context :json_client_for_authenticated_token_admin

    it "allows access to context resources with a valid token" do
      response = client.get("/api-v2/admin/contexts")
      expect(response.status).to eq(200)

      context_id = response.body.first["id"]
      response = client.get("/api-v2/admin/contexts/#{context_id}")
      expect(response.status).to eq(200)
      expect(response.body["id"]).to eq(context_id)
    end
  end
end

describe "Admin Context Resource Access" do
  let(:context_attrs) do
    {
      id: "test_context",
      admin_comment: "Test admin comment",
      labels: {en: "English Label", de: "Deutsches Label"},
      descriptions: {en: "English Description", de: "Deutsche Beschreibung"}
    }
  end

  let(:context_attrs2) do
    {
      admin_comment: "Updated admin comment v2",
      labels: {en: "English Label", de: "Deutsches Label"},
      descriptions: {en: "English Description", de: "Deutsche Beschreibung"}
    }
  end

  context "Authenticated access to context resources" do
    include_context :json_client_for_authenticated_token_admin

    it "performs full CRUD on context resource" do
      # CREATE
      post_response = client.post("/api-v2/admin/contexts") do |req|
        req.body = context_attrs.to_json
        req.headers["Content-Type"] = "application/json"
      end

      # binding.pry
      expect(post_response.status).to eq(200)
      created_id = post_response.body["id"]
      expect(created_id).not_to be_nil

      # READ (GET)
      get_response = client.get("/api-v2/admin/contexts/#{created_id}")
      expect(get_response.status).to eq(200)
      expect(get_response.body["id"]).to eq(created_id)
      expect(get_response.body["labels"]["en"]).to eq("English Label")

      # UPDATE (PUT)
      put_response = client.put("/api-v2/admin/contexts/#{created_id}") do |req|
        req.body = context_attrs2.to_json
        req.headers["Content-Type"] = "application/json"
      end
      expect(put_response.status).to eq(200)
      expect(put_response.body["admin_comment"]).to eq("Updated admin comment v2")

      # DELETE
      delete_response = client.delete("/api-v2/admin/contexts/#{created_id}")
      expect(delete_response.status).to eq(200)

      # VERIFY DELETION
      get_deleted = client.get("/api-v2/admin/contexts/#{created_id}")
      expect(get_deleted.status).to eq(404)
    end
  end
end
