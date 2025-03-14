require "spec_helper"
require "shared/audit-validator"

describe "roles" do
  before :each do
    @roles = 210.times.map do |i|
      FactoryBot.create :role, labels: {de: "Role #{i}"}
    end
  end

  include_context :authenticated_json_client do
    describe "get roles without pagination" do
      it "responses with 200" do
        resp1 = authenticated_json_client.get("/api-v2/roles?page=1&size=5")
        expect(resp1.status).to be == 200
        expect(resp1.body["data"].count).to be 5
        expect(resp1.body["pagination"]).to be

        resp2 = authenticated_json_client.get("/api-v2/roles?page=2&size=5")
        expect(resp2.status).to be == 200
        expect(resp2.body["data"].count).to be 5
        expect(resp2.body["pagination"]).to be

        expect(lists_of_maps_different?(resp1.body["data"], resp2.body["data"])).to eq true
      end

      it "responses with 200" do
        resp1 = authenticated_json_client.get("/api-v2/roles")
        expect(resp1.status).to be == 200
        expect(resp1.body["roles"].count).to be 201
      end

      it "responses with 200" do
        resp1 = authenticated_json_client.get("/api-v2/roles")
        expect(resp1.status).to be == 200
        expect(resp1.body["roles"].count).to be 201

        role_id = resp1.body["roles"].first["id"]
        resp1 = authenticated_json_client.get("/api-v2/roles/#{role_id}")
        expect(resp1.status).to be == 200
        expect(resp1.body).to be_a Hash
      end
    end

    describe "get roles" do
      let :roles_result do
        authenticated_json_client.get("/api-v2/roles?page=1&size=100")
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
