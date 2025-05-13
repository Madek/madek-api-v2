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

        # TODO json roa remove: get groups collection by id
        # it 'using the roa collection we can retrieve all groups' do
        #  set_of_created_ids = Set.new(@groups.map(&:id))
        #  set_of_retrieved_ids = Set.new(groups_result.collection().map(&:get).map{|x| x.data['id']})
        #  expect(set_of_retrieved_ids.count).to be== set_of_created_ids.count
        #  expect(set_of_retrieved_ids).to be== set_of_created_ids
        # end
      end
    end
  end
end
