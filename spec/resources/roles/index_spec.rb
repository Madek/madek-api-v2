require "spec_helper"
require "shared/audit-validator"

describe "roles" do
  before :each do
    @roles = 201.times.map do |i|
      FactoryBot.create :role, labels: {de: "Role #{i}"}
    end
  end

  include_context :authenticated_json_client do
    describe "get roles with pagination" do
      it "responses with 200" do
        resp1 = authenticated_json_client.get("/api-v2/roles?page=0&size=5")
        expect(resp1.status).to be == 200
        expect(resp1.body["roles"].count).to be 5

        resp2 = authenticated_json_client.get("/api-v2/roles?page=1&size=5")
        expect(resp2.status).to be == 200
        expect(resp2.body["roles"].count).to be 5

        expect(lists_of_maps_different?(resp1.body["roles"], resp2.body["roles"])).to eq true
      end
    end

    describe "get roles" do
      let :roles_result do
        authenticated_json_client.get("/api-v2/roles?page=0&size=100")
      end

      it "responses with 200" do
        expect(roles_result.status).to be == 200
      end

      it "returns some data but less than created because we paginate" do
        expect(
          roles_result.body["roles"].count
        ).to be < @roles.count
      end

      # TODO json roa remove: get roles collection
      # it 'retrieves all roles using the collection' do
      #  set_of_created_ids = Set.new(@roles.map(&:id))
      #  set_of_retrieved_ids = Set.new(roles_result.collection.map(&:get).map { |x| x.data['id'] })
      #  expect(set_of_retrieved_ids.count - set_of_created_ids.count).to be_zero
      # end
    end
  end
end
