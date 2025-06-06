require "spec_helper"

describe "Meta Keys API - Index Endpoint" do
  context "as a token user" do
    include_context :json_client_for_authenticated_token_user

    it "returns public meta-keys without pagination" do
      response = client.get("/api-v2/meta-keys/")
      expect(response.status).to eq(200)
      expect(response.body["meta-keys"]).to be_an(Array)
      expect(response.body["meta-keys"].count).to eq(8)
    end

    it "returns public meta-keys with pagination" do
      response = client.get("/api-v2/meta-keys/?page=1&size=5")
      expect(response.status).to eq(200)
      expect(response.body["data"]).to be_an(Array)
      expect(response.body["pagination"]).to be_a(Hash)
    end

    it "denies access to admin meta-keys without pagination" do
      response = client.get("/api-v2/admin/meta-keys/")
      expect(response.status).to eq(403)
    end

    it "denies access to admin meta-keys with pagination" do
      response = client.get("/api-v2/admin/meta-keys/?page=1&size=5")
      expect(response.status).to eq(403)
    end
  end

  context "as an admin user" do
    include_context :json_client_for_authenticated_token_admin

    it "returns admin meta-keys without pagination" do
      response = client.get("/api-v2/admin/meta-keys/")
      expect(response.status).to eq(200)
      expect(response.body["meta-keys"]).to be_an(Array)
      expect(response.body["meta-keys"].count).to eq(8)
    end

    it "returns admin meta-keys with pagination" do
      response = client.get("/api-v2/admin/meta-keys/?page=1&size=5")
      expect(response.status).to eq(200)
      expect(response.body["data"]).to be_an(Array)
      expect(response.body["pagination"]).to be_a(Hash)
    end
  end
end
