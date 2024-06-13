require "spec_helper"
require "shared/audit-validator"

expected_audit_entries = ["UPDATE auth_systems", "INSERT groups", "INSERT rdf_classes", "INSERT rdf_classes",
  "INSERT people", "INSERT usage_terms", "INSERT users", "INSERT auth_systems_users",
  "INSERT admins", "INSERT people"]

context "people" do
  context "admin user" do
    include_context :json_client_for_authenticated_admin_user do
      describe "creating" do
        describe "a person" do
          it "works" do
            expect(client.post("/api-v2/admin/people/") do |req|
              # client.get.relation('people').post do |req|
              req.body = {last_name: "test",
                          subtype: "Person"}.to_json
              req.headers["Content-Type"] = "application/json"
            end.status).to be == 201
          end
        end

        describe "an institutional person" do
          it "works" do
            expect(client.post("/api-v2/admin/people/") do |req|
              req.body = {first_name: nil,
                          last_name: "Bachelor",
                          pseudonym: "BA.alle",
                          institutional_id: "162645.alle",
                          subtype: "PeopleInstitutionalGroup"}.to_json
              req.headers["Content-Type"] = "application/json"
            end.status).to be == 201

            expect_audit_entries("POST /api-v2/admin/people/", expected_audit_entries, 201)
          end
        end
      end

      describe "a via post created person" do
        let :created_person do
          client.post("/api-v2/admin/people/") do |req|
            req.body = {subtype: "PeopleInstitutionalGroup",
                        institutional_id: "12345/x",
                        last_name: "test"}.to_json
            req.headers["Content-Type"] = "application/json"
          end
        end
        describe "the data" do
          it "has the proper subtype" do
            expect(created_person.body["subtype"]).to be == "PeopleInstitutionalGroup"
          end
          it "has the proper institutional_id" do
            expect(created_person.body["institutional_id"]).to be == "12345/x"

            expect_audit_entries("POST /api-v2/admin/people/", expected_audit_entries, 201)
          end
        end
      end
    end
  end
end
