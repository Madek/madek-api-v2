require "spec_helper"

context "groups" do
  before :each do
    @group = FactoryBot.create :group
  end

  context "admin user" do
    include_context :json_client_for_authenticated_token_admin do
      describe "patching/updating" do
        it "works" do
          expect(
            client.patch("/api-v2/admin/groups/#{@group.id}") do |req|
              req.body = {name: "new name"}.to_json
              req.headers["Content-Type"] = "application/json"
            end.status
          ).to be == 200
        end

        it "works when we do no changes" do
          expect(
            client.patch("/api-v2/admin/groups/#{@group.id}") do |req|
              req.body = {name: @group.name}.to_json
              req.headers["Content-Type"] = "application/json"
            end.status
          ).to be == 200
        end

        context "patch result" do
          let :patch_result do
            client.patch("/api-v2/admin/groups/#{@group.id}") do |req|
              req.body = {name: "new name"}.to_json
              req.headers["Content-Type"] = "application/json"
            end
          end
          it "contains the update" do
            expect(patch_result.body["name"]).to be == "new name"
          end
        end

        it "can update is_assignable to false" do
          resp = client.patch("/api-v2/admin/groups/#{@group.id}") do |req|
            req.body = {is_assignable: false}.to_json
            req.headers["Content-Type"] = "application/json"
          end
          expect(resp.status).to be == 200
          expect(resp.body["is_assignable"]).to eq false

          get_resp = client.get("/api-v2/admin/groups/#{@group.id}")
          expect(get_resp.status).to be == 200
          expect(get_resp.body["is_assignable"]).to eq false
        end

        it "can toggle is_assignable false to true" do
          # first set to false
          client.patch("/api-v2/admin/groups/#{@group.id}") do |req|
            req.body = {is_assignable: false}.to_json
            req.headers["Content-Type"] = "application/json"
          end
          mid = client.get("/api-v2/admin/groups/#{@group.id}")
          expect(mid.body["is_assignable"]).to eq false

          # then set back to true
          resp = client.patch("/api-v2/admin/groups/#{@group.id}") do |req|
            req.body = {is_assignable: true}.to_json
            req.headers["Content-Type"] = "application/json"
          end
          expect(resp.status).to be == 200
          expect(resp.body["is_assignable"]).to eq true

          fin = client.get("/api-v2/admin/groups/#{@group.id}")
          expect(fin.status).to be == 200
          expect(fin.body["is_assignable"]).to eq true
        end
      end
    end
  end
end
