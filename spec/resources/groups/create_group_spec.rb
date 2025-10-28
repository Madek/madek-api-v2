require "spec_helper"

context "groups" do
  context "admin user" do
    include_context :json_client_for_authenticated_token_admin do
      describe "creating" do
        describe "a group" do
          it "works" do
            expect(client.post("/api-v2/admin/groups/") do |req|
              req.body = {name: "test"}.to_json
              req.headers["Content-Type"] = "application/json"
            end.status).to be == 201
          end

          it "can be created with is_assignable=false" do
            resp = client.post("/api-v2/admin/groups/") do |req|
              req.body = {name: "assignable-false", is_assignable: false}.to_json
              req.headers["Content-Type"] = "application/json"
            end
            expect(resp.status).to be == 201
            expect(resp.body["is_assignable"]).to eq false
          end

          it "can be created with is_assignable=true" do
            resp = client.post("/api-v2/admin/groups/") do |req|
              req.body = {name: "assignable-true", is_assignable: true}.to_json
              req.headers["Content-Type"] = "application/json"
            end
            expect(resp.status).to be == 201
            expect(resp.body["is_assignable"]).to eq true
          end
        end

        describe "an institutional group" do
          it "works" do
            expect(client.post("/api-v2/admin/groups/") do |req|
              req.body = {type: "InstitutionalGroup",
                          institutional_id: "12345_x",
                          name: "test"}.to_json
              req.headers["Content-Type"] = "application/json"
            end.status).to be == 201
          end

          it "can be created with is_assignable=false" do
            resp = client.post("/api-v2/admin/groups/") do |req|
              req.body = {type: "InstitutionalGroup",
                          institutional_id: "12345_false",
                          name: "test-false",
                          is_assignable: false}.to_json
              req.headers["Content-Type"] = "application/json"
            end
            expect(resp.status).to be == 201
            expect(resp.body["is_assignable"]).to eq false
          end

          it "can be created with is_assignable=true" do
            resp = client.post("/api-v2/admin/groups/") do |req|
              req.body = {type: "InstitutionalGroup",
                          institutional_id: "12345_true",
                          name: "test-true",
                          is_assignable: true}.to_json
              req.headers["Content-Type"] = "application/json"
            end
            expect(resp.status).to be == 201
            expect(resp.body["is_assignable"]).to eq true
          end
        end
      end

      describe "a via post created group" do
        let :created_group do
          client.post("/api-v2/admin/groups/") do |req|
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
          it "has the default for is_assignable" do
            expect(created_group.body["is_assignable"]).to eq true
          end
        end
      end
    end
  end
end
