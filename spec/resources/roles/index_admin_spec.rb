require "spec_helper"
require "shared/audit-validator"

describe "roles" do
  before :each do
    @roles = 201.times.map do |i|
      FactoryBot.create :role, labels: {de: "Role #{i}"}
    end
  end

  include_context :json_client_for_authenticated_token_admin do
    describe "modify roles" do
      it "responses with 200" do
        mk_key = "madek_test:title"

        resp = client.post("/api-v2/admin/roles") do |req|
          req.body = {
            meta_key_id: mk_key, labels: {de: "Rolle 1", en: "Role 1"}
          }.to_json
          req.headers["Content-Type"] = "application/json"
        end
        expect(resp.status).to be == 422

        FactoryBot.create(:meta_key_text, id: mk_key)
        resp = client.post("/api-v2/admin/roles") do |req|
          req.body = {
            meta_key_id: mk_key, labels: {de: "Rolle 1", en: "Role 1"}
          }.to_json
          req.headers["Content-Type"] = "application/json"
        end
        expect(resp.status).to be == 200

        role_id = resp.body["id"]

        resp = client.put("/api-v2/admin/roles/#{role_id}") do |req|
          req.body = {
            # meta_key_id: mk_key,
            labels: {de: "Rolle 2", en: "Role 2"}
          }.to_json
          req.headers["Content-Type"] = "application/json"
        end
        expect(resp.status).to be == 200

        resp = client.delete("/api-v2/admin/roles/#{role_id}")
        expect(resp.status).to be == 200
      end
    end

    describe "get roles with pagination" do
      it "responses with 200" do
        resp1 = client.get("/api-v2/admin/roles?page=1&size=5")
        expect(resp1.status).to be == 200
        expect(resp1.body["data"].count).to be 5
        expect(resp1.body["pagination"]).to be

        resp2 = client.get("/api-v2/admin/roles?page=2&size=5")
        expect(resp2.status).to be == 200
        expect(resp2.body["data"].count).to be 5
        expect(resp2.body["pagination"]).to be

        expect(lists_of_maps_different?(resp1.body["data"], resp2.body["data"])).to eq true
      end

      it "responses with 200" do
        resp1 = client.get("/api-v2/admin/roles")
        expect(resp1.status).to be == 200
        expect(resp1.body["roles"].count).to be 201
      end

      it "responses with 200" do
        resp1 = client.get("/api-v2/admin/roles")
        expect(resp1.status).to be == 200
        expect(resp1.body["roles"].count).to be 201

        role_id = resp1.body["roles"].first["id"]
        resp1 = client.get("/api-v2/admin/roles/#{role_id}")
        expect(resp1.status).to be == 200
        expect(resp1.body).to be_a Hash
      end
    end

    describe "get roles" do
      let :roles_result do
        client.get("/api-v2/admin/roles?page=1&size=100")
      end

      it "responses with 200" do
        expect(roles_result.status).to be == 200
      end

      it "returns some data but less than created because we paginate" do
        expect(
          roles_result.body["data"].count
        ).to be < @roles.count
      end
    end
  end
end
