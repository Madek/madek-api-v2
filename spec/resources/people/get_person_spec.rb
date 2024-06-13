require "spec_helper"
require "hashdiff"
require "shared/audit-validator"

context "people" do
  before :each do
    @person = FactoryBot.create(:person, external_uris: ["http://example.com"])
    @person = @person.reload
  end

  context "admin user" do
    include_context :json_client_for_authenticated_admin_user do
      context "retriving a standard person" do
        let :get_person_result do
          client.get("/api-v2/people/#{@person.id}")
        end

        it "works" do
          expect(get_person_result.status).to be == 200

          expected_audit_entries = ["UPDATE auth_systems", "INSERT groups", "INSERT rdf_classes", "INSERT rdf_classes",
            "INSERT people", "INSERT people", "INSERT usage_terms", "INSERT users",
            "INSERT auth_systems_users", "INSERT admins"]
          expect_audit_entries("GET /api-v2/people/#{@person.id}", expected_audit_entries, 200, OPT_CHANGE_AUDITS_ONLY)
        end

        it "has the proper data" do
          person = get_person_result.body
          expect(
            person.with_indifferent_access.except(:created_at, :updated_at, :searchable)
          ).to eq(
            @person.attributes
              .with_indifferent_access.except(:created_at, :updated_at, :searchable)
          )
        end
      end

      context "a institunal person (with naughty institutional_id)" do
        before :each do
          @inst_person = FactoryBot.create :people_instgroup,
            institution: "fake-university.com",
            institutional_id: "https://fake-university.com/students/12345"
        end

        url = "/api-v2/people/" +
          CGI.escape(["fake-university.com", "https://fake-university.com/students/12345"].to_json)
        let :result do
          client.get(url)
        end

        it "can be retrieved by the pair [institution, institutional_id]" do
          expect(result.status).to be == 200
          expect(result.body["id"]).to be == @inst_person["id"]

          expected_audit_entries = ["UPDATE auth_systems", "INSERT groups", "INSERT rdf_classes", "INSERT rdf_classes",
            "INSERT people", "INSERT people", "INSERT people", "INSERT usage_terms",
            "INSERT users", "INSERT auth_systems_users", "INSERT admins"]
          expect_audit_entries("GET #{url}}", expected_audit_entries, 200, OPT_CHANGE_AUDITS_ONLY)
        end
      end
    end
  end
end
