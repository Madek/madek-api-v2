require "spec_helper"
require "shared/audit-validator"

context "users" do
  before :each do
    @users = 201.times.map { FactoryBot.create :user }
  end

  context "admin user" do
    include_context :json_client_for_authenticated_token_admin do
      # more tests needed for query parameters:
      #   * email,
      #   * login,
      #   * institution,
      #   * institutional_id,
      #   *  and full text search

      describe "get users" do
        let :users_result do
          client.get("/api-v2/admin/users?page=1&size=100")
        end

        it "responses with 200" do
          expect(users_result.status).to be == 200
        end

        it "returns some data but less than created because we paginate" do
          expect(
            users_result.body["data"].count
          ).to be < @users.count
        end
      end

      describe "get users by pagination" do
        it "responses with 200" do
          resp1 = client.get("/api-v2/admin/users?page=1&size=5")
          expect(resp1.status).to be == 200
          expect(resp1.body["data"].count).to be 5
          expect(resp1.body["pagination"]).to be

          resp2 = client.get("/api-v2/admin/users?page=2&size=5")
          expect(resp2.status).to be == 200
          expect(resp2.body["data"].count).to be 5
          expect(resp2.body["pagination"]).to be

          expect(lists_of_maps_different?(resp1.body["data"], resp2.body["data"])).to eq true
        end

        it "responses with 200" do
          resp = client.get("/api-v2/admin/users")
          expect(resp.status).to be == 200
          expect(resp.body["users"].count).to be 202
          expect(resp.body["pagination"]).not_to be
        end
      end
    end
  end
end
