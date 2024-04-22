require "spec_helper"

context "groups" do
  context "admin user" do
    include_context :json_client_for_authenticated_admin_user do
      # include_context :json_roa_client_for_authenticated_admin_user do

      describe "creating" do
        describe "a group" do
          it "works" do
            expect(client.post("/api/admin/groups/") do |req|
              req.body = {name: "test"}.to_json
              req.headers["Content-Type"] = "application/json"
            end.status).to be == 201
          end
        end

        describe "an institutional group" do
          it "works" do
            expect(client.post("/api/admin/groups/") do |req|
              req.body = {type: "InstitutionalGroup",
                          institutional_id: "12345_x",
                          name: "test"}.to_json
              req.headers["Content-Type"] = "application/json"
            end.status).to be == 201
          end
        end
      end

      describe "a via post created group" do
        let :created_group do
          client.post("/api/admin/groups/") do |req|
            req.body = {type: "InstitutionalGroup",
                        institutional_id: "12345/x",
                        name: "test"}.to_json
            req.headers["Content-Type"] = "application/json"
          end
        end
        describe "the data" do
          it "has the proper type" do
            expect(created_group.body["type"]).to be == "InstitutionalGroup"
          end
          it "has the proper institutional_id" do
            expect(created_group.body["institutional_id"]).to be == "12345/x"
          end
        end
      end
    end
  end
end
