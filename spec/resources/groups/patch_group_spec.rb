require "spec_helper"

context "groups" do
  before :each do
    @group = FactoryBot.create :group
  end

  context "admin user" do
    include_context :json_client_for_authenticated_admin_user do
      describe "patching/updating" do
        it "works" do
          expect(
            client.put("/api/admin/groups/#{@group.id}") do |req|
              req.body = {name: "new name"}.to_json
              req.headers["Content-Type"] = "application/json"
            end.status
          ).to be == 200
        end

        it "works when we do no changes" do
          expect(
            client.put("/api/admin/groups/#{@group.id}") do |req|
              req.body = {name: @group.name}.to_json
              req.headers["Content-Type"] = "application/json"
            end.status
          ).to be == 200
        end

        context "patch result" do
          let :patch_result do
            client.put("/api/admin/groups/#{@group.id}") do |req|
              req.body = {name: "new name"}.to_json
              req.headers["Content-Type"] = "application/json"
            end
          end
          it "contains the update" do
            expect(patch_result.body["name"]).to be == "new name"
          end
        end
      end
    end
  end
end
