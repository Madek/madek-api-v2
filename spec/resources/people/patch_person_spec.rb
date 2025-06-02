require "spec_helper"
require "shared/audit-validator"

context "people" do
  before :each do
    @person = FactoryBot.create :person
  end

  context "admin user" do
    include_context :json_client_for_authenticated_token_admin do
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
            "INSERT auth_systems_users", "INSERT admins", "INSERT api_tokens", "UPDATE people"]

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
            "INSERT people", "INSERT people", "INSERT usage_terms", "INSERT users", "INSERT auth_systems_users",
            "INSERT admins", "INSERT api_tokens"]

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
              "INSERT auth_systems_users", "INSERT admins", "INSERT api_tokens", "UPDATE people"]

            expect_audit_entries("PATCH /api-v2/admin/people/#{CGI.escape(@person.id)}", expected_audit_entries, 200)
          end
        end
      end
      describe "patching field by field" do
        def patch_request(body = {})
          client.patch("/api-v2/admin/people/#{CGI.escape(@person.id)}") do |req|
            req.body = body.to_json
            req.headers["Content-Type"] = "application/json"
          end
        end

        it "returns status 422 when field does not exist" do
          response = patch_request(foo: "bar")
          expect(response.status).to eq(422)
        end

        it "updates first_name" do
          response = patch_request(first_name: "Homer")
          expect(response.status).to eq(200)
          expect(@person.reload.first_name).to be == "Homer"
        end

        it "updates last_name" do
          response = patch_request(last_name: "Simpson")
          expect(response.status).to eq(200)
          expect(@person.reload.last_name).to be == "Simpson"
        end

        it "updates pseudonym" do
          response = patch_request(pseudonym: "Humi")
          expect(response.status).to eq(200)
          expect(@person.reload.pseudonym).to be == "Humi"
        end

        it "updates admin_comment" do
          response = patch_request(admin_comment: "Lorem ipsum")
          expect(response.status).to eq(200)
          expect(@person.reload.admin_comment).to be == "Lorem ipsum"
        end

        it "updates subtype" do
          expect(@person.subtype).to be == "Person"
          response = patch_request(subtype: "PeopleGroup")
          expect(response.status).to eq(200)
          expect(@person.reload.subtype).to be == "PeopleGroup"
        end

        it "updates description" do
          response = patch_request(description: "Bla bla")
          expect(response.status).to eq(200)
          expect(@person.reload.description).to be == "Bla bla"
        end

        it "updates external_uris" do
          response = patch_request(external_uris: ["http://www.foo", "http://www.bar"])
          expect(response.status).to eq(200)
          expect(@person.reload.external_uris).to be == ["http://www.foo", "http://www.bar"]
        end

        it "updates external_uris to empty array" do
          @person.update(external_uris: ["http://www.foo"])
          expect(@person.reload.external_uris).to be == ["http://www.foo"]
          response = patch_request(external_uris: [])
          expect(response.status).to eq(200)
          expect(@person.reload.external_uris).to be == []
        end

        it "updates institution and institutional_id" do
          response = patch_request(institution: "Uni", institutional_id: "T001")
          expect(response.status).to eq(200)
          @person.reload
          expect(@person.institution).to be == "Uni"
          expect(@person.institutional_id).to be == "T001"
        end

        it "updates institutional_id to nil" do
          @person.update(institution: "Uni", institutional_id: "T001")

          response = patch_request(institutional_id: nil)
          expect(response.status).to eq(200)
          @person.reload
          expect(@person.institution).to be == "Uni"
          expect(@person.institutional_id).to be_nil
        end

        it "updates identification_info" do
          response = patch_request(identification_info: "Müller 2")
          expect(response.status).to eq(200)
          expect(@person.reload.identification_info).to be == "Müller 2"
        end

        it "updates institutional_directory_infos" do
          response = patch_request(institutional_directory_infos: ["Staff", "Stud Philosophy_H14, Biology_F20"])
          expect(response.status).to eq(200)
          expect(@person.reload.institutional_directory_infos).to be == ["Staff", "Stud Philosophy_H14, Biology_F20"]
        end

        it "updates institutional_directory_infos to empty array" do
          @person.update(institutional_directory_infos: ["Staff"])
          expect(@person.reload.institutional_directory_infos).to be == ["Staff"]

          response = patch_request(institutional_directory_infos: [])
          expect(response.status).to eq(200)
          expect(@person.reload.institutional_directory_infos).to be == []
        end

        it "updates institutional_directory_infos to empty array also when nil is given" do
          @person.update(institutional_directory_infos: ["Staff"])
          expect(@person.reload.institutional_directory_infos).to be == ["Staff"]

          response = patch_request(institutional_directory_infos: nil)
          expect(response.status).to eq(200)
          expect(@person.reload.institutional_directory_infos).to be == []
        end

        it "updates institutional_directory_inactive_since with isoTS" do
          response = patch_request(institutional_directory_inactive_since: "2023-01-01T12:02:00+10")
          expect(response.status).to eq(200)
          expected_time = Time.zone.parse("2023-01-01T12:02:00+10")
          expect(@person.reload.institutional_directory_inactive_since).to eq(expected_time)
        end

        it "updates institutional_directory_inactive_since with timestamptz" do
          response = patch_request(institutional_directory_inactive_since: "2023-01-01 11:13:06.264577+02")
          expect(response.status).to eq(200)
          expected_time = Time.zone.parse("2023-01-01 11:13:06.264577+02")
          expect(@person.reload.institutional_directory_inactive_since).to eq(expected_time)
        end

        it "updates institutional_directory_inactive_since with timestamptz" do
          response = patch_request(institutional_directory_inactive_since: nil)
          expect(response.status).to eq(200)
          expect(@person.reload.institutional_directory_inactive_since).to eq(nil)
        end

        it "updates institutional_directory_inactive_since to nil" do
          old_time = Time.zone.parse("2023-01-01 11:13:06.264577+02")
          @person.update(institutional_directory_inactive_since: old_time)
          expect(@person.reload.institutional_directory_inactive_since).to eq(old_time)

          response = patch_request(institutional_directory_inactive_since: nil)
          expect(response.status).to eq(200)
          expect(@person.reload.institutional_directory_inactive_since).to be_nil
        end
      end
    end
  end
end
