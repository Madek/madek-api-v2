require "spec_helper"
require "shared/audit-validator"

context "groups" do
  before :each do
    @groups = 201.times.map { FactoryBot.create :group }

    8.times do
      @groups.first.users << FactoryBot.create(:user)
    end
  end

  context "non admin-user" do
    include_context :json_client_for_authenticated_token_user do
      it "is forbidden to retrieve groups" do
        expect(
          client.get("/api-v2/admin/groups/").status
        ).to be == 403
      end
    end
  end

  context "Getting groups with pagination for admin" do
    include_context :json_client_for_authenticated_token_admin do
      it "responses with 200" do
        resp1 = client.get("/api-v2/admin/groups/?page=1&size=5")
        expect(resp1.status).to be == 200
        expect(resp1.body["data"].count).to be 5

        resp2 = client.get("/api-v2/admin/groups/?page=2&size=5")
        expect(resp2.status).to be == 200
        expect(resp2.body["data"].count).to be 5

        expect(lists_of_maps_different?(resp1.body["data"], resp2.body["data"])).to eq true
      end

      it "responses with 200" do
        resp1 = client.get("/api-v2/admin/groups/")
        expect(resp1.status).to be == 200
        expect(resp1.body["groups"].count).to be 202
      end

      context "get users of group" do
        it "responses with 200" do
          resp = client.get("/api-v2/admin/groups/#{@groups.first.id}/users/")
          expect(resp.status).to be == 200
          expect(resp.body["users"].count).to be 8
          expect(resp.body["users"]).to be_a Array
        end

        it "responses with 200" do
          resp = client.get("/api-v2/admin/groups/#{@groups.first.id}/users/?page=1&size=5")
          expect(resp.status).to be == 200
          expect(resp.body["data"]).to be_a Array
          expect(resp.body["data"].count).to be 5
          expect(resp.body["pagination"]).to be_a Hash
        end
      end

      it "responses with 200 for all/single" do
        resp1 = client.get("/api-v2/groups/")
        expect(resp1.status).to be == 200
        expect(resp1.body["groups"].count).to be 202

        group_id = resp1.body["groups"].first["id"]
        resp1 = client.get("/api-v2/groups/#{group_id}")
        expect(resp1.status).to be == 200
        expect(resp1.body).to be_a Hash
      end
    end
  end

  context "Getting groups with pagination for user" do
    include_context :json_client_for_authenticated_token_user do
      it "responses with 200" do
        resp1 = client.get("/api-v2/groups/?page=1&size=5")
        expect(resp1.status).to be == 200
        expect(resp1.body["data"].count).to be 5

        resp2 = client.get("/api-v2/groups/?page=2&size=5")
        expect(resp2.status).to be == 200
        expect(resp2.body["data"].count).to be 5

        expect(lists_of_maps_different?(resp1.body, resp2.body)).to eq true
      end

      it "responses with 200" do
        resp1 = client.get("/api-v2/groups/")
        expect(resp1.status).to be == 200
        expect(resp1.body["groups"].count).to be 202
      end

      it "responses with 200 for all but 403 for single" do
        resp = client.get("/api-v2/groups/")
        expect(resp.status).to be == 200
        expect(resp.body["groups"].count).to be 202

        group_id = resp.body["groups"].first["id"]
        resp = client.get("/api-v2/groups/#{group_id}")
        expect(resp.status).to be == 403
      end
    end
  end

  context "Getting groups with pagination for admin" do
    include_context :json_client_for_authenticated_token_admin do
      describe "get groups" do
        let :groups_result do
          client.get("/api-v2/admin/groups/?page=1&size=100")
        end

        it "responses with 200" do
          expect(groups_result.status).to be == 200
        end

        it "returns some data but less than created because we paginate" do
          expect(
            groups_result.body["data"].count
          ).to be < @groups.count
        end

        it "includes is_assignable key for each group item" do
          expect(groups_result.body["data"]).not_to be_empty
          expect(groups_result.body["data"].all? { |g| g.key?("is_assignable") }).to eq true
        end
      end
    end
  end

  context "Filter groups by is_assignable for admin" do
    include_context :json_client_for_authenticated_token_admin do
      before :each do
        @non_assignable_group = FactoryBot.create(:group, is_assignable: false)
      end

      it "returns only non-assignable groups when is_assignable=false" do
        result = client.get("/api-v2/admin/groups/?is_assignable=false")
        expect(result.status).to be == 200
        data = result.body["groups"] || result.body["data"]
        expect(data).not_to be_nil
        expect(data.all? { |g| g["is_assignable"] == false }).to eq true
        ids = data.map { |g| g["id"] }
        expect(ids).to include(@non_assignable_group.id)
      end
    end
  end
end
