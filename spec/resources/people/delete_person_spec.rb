require "spec_helper"
require "shared/audit-validator"

expected_audit_entries = ["UPDATE auth_systems", "INSERT groups", "INSERT rdf_classes", "INSERT rdf_classes",
  "INSERT people", "INSERT people", "INSERT usage_terms", "INSERT users",
  "INSERT auth_systems_users", "INSERT admins", "INSERT api_tokens", "DELETE people"]

context "people" do
  before :each do
    @person = FactoryBot.create :person
  end

  context "non admin-user" do
    include_context :json_client_for_authenticated_token_user do
      it "is forbidden to delete any person" do
        expect(
          client.delete("/api-v2/admin/people/#{@person.id}").status
        ).to be == 403
      end
    end
  end

  context "admin-user" do
    include_context :json_client_for_authenticated_token_admin do
      context "deleting a standard person" do
        let :delete_person_result do
          client.delete("/api-v2/admin/people/#{@person.id}")
        end

        it "returns the expected status code 200" do
          expect(delete_person_result.status).to be == 204

          expect_audit_entries("DELETE /api-v2/admin/people/#{@person.id}", expected_audit_entries, 204)
        end

        it "effectively removes the person" do
          expect(delete_person_result.status).to be == 204
          expect(Person.find_by(id: @person.id)).not_to be

          expect_audit_entries("DELETE /api-v2/admin/people/#{@person.id}", expected_audit_entries, 204)
        end
      end
    end
  end
end
