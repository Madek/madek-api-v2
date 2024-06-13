require "spec_helper"
require "shared/audit-validator"

context "people" do
  before :each do
    @person = FactoryBot.create :person
  end

  context "admin user" do
    include_context :json_client_for_authenticated_admin_user do
      describe "patching/updating" do
        it "works" do
          expect(
            client.patch("/api-v2/admin/people/#{CGI.escape(@person.id)}") do |req|
              req.body = {last_name: "new name"}.to_json
              req.headers["Content-Type"] = "application/json"
            end.status
          ).to be == 200

          expected_audit_entries = ["UPDATE auth_systems", "INSERT groups", "INSERT rdf_classes", "INSERT rdf_classes",
            "INSERT people", "INSERT people", "INSERT usage_terms", "INSERT users",
            "INSERT auth_systems_users", "INSERT admins", "UPDATE people"]
          expect_audit_entries("PATCH /api-v2/admin/people/#{CGI.escape(@person.id)}", expected_audit_entries, 200)
        end

        it "works when we do no changes" do
          expect(
            client.patch("/api-v2/admin/people/#{CGI.escape(@person.id)}") do |req|
              req.body = {last_name: @person.last_name}.to_json
              req.headers["Content-Type"] = "application/json"
            end.status
          ).to be == 200

          expected_audit_entries = ["UPDATE auth_systems", "INSERT groups", "INSERT rdf_classes", "INSERT rdf_classes",
            "INSERT people", "INSERT people", "INSERT usage_terms", "INSERT users",
            "INSERT auth_systems_users", "INSERT admins"]
          expect_audit_entries("PATCH /api-v2/admin/people/#{CGI.escape(@person.id)}", expected_audit_entries, 200)
        end

        context "patch result" do
          let :patch_result do
            client.patch("/api-v2/admin/people/#{CGI.escape(@person.id)}") do |req|
              req.body = {last_name: "new name"}.to_json
              req.headers["Content-Type"] = "application/json"
            end
          end
          it "contains the update" do
            expect(patch_result.body["last_name"]).to be == "new name"

            expected_audit_entries = ["UPDATE auth_systems", "INSERT groups", "INSERT rdf_classes", "INSERT rdf_classes",
              "INSERT people", "INSERT people", "INSERT usage_terms", "INSERT users",
              "INSERT auth_systems_users", "INSERT admins", "UPDATE people"]
            expect_audit_entries("PATCH /api-v2/admin/people/#{CGI.escape(@person.id)}", expected_audit_entries, 200)
          end
        end
      end
    end
  end
end
