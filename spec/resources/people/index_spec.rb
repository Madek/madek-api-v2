require "spec_helper"
require "shared/audit-validator"

context "people" do
  expected_audit_entries = ["INSERT admins", "INSERT api_tokens", "INSERT auth_systems_users", "INSERT groups",
    "INSERT people", "INSERT rdf_classes", "INSERT usage_terms", "INSERT users", "UPDATE auth_systems"]

  before :each do
    @people = 77.times.map {
      FactoryBot.create :person,
        institution: "foo.com"
    }
  end

  before :each do
    @people = 77.times.map {
      FactoryBot.create :people_group,
        institution: "foo.com"
    }
  end

  before :each do
    @people = 77.times.map {
      FactoryBot.create :people_instgroup,
        institution: "foo.com"
    }
  end

  context "admin user" do
    include_context :json_client_for_authenticated_token_admin do
      describe "get an unfiltered people list as an admin" do
        url = "/api-v2/admin/people/"
        let :result do
          client.get(url)
        end

        it "responses with 200" do
          expect(result.status).to be == 200
          expect_audit_entries("GET #{url}}", expected_audit_entries, 200, OPT_DISTINCT_CHANGE_AUDITS_ONLY)
        end

        it "returns the count of requested items" do
          expect(result.body["people"].count).to be == 232
          expect_audit_entries("GET #{url}}", expected_audit_entries, 200, OPT_DISTINCT_CHANGE_AUDITS_ONLY)
        end
      end

      describe "get an unfiltered people list as an admin" do
        url = "/api-v2/admin/people/?page=1&size=100"
        let :result do
          # client.get.relation('people').get()
          client.get(url)
        end

        it "responses with 200" do
          expect(result.status).to be == 200

          expect_audit_entries("GET #{url}}", expected_audit_entries, 200, OPT_DISTINCT_CHANGE_AUDITS_ONLY)
        end

        it "returns the count of requested items" do
          expect(result.body["data"].count).to be == 100

          expect_audit_entries("GET #{url}}", expected_audit_entries, 200, OPT_DISTINCT_CHANGE_AUDITS_ONLY)
        end
      end

      context "filter people by their institution" do
        url = "/api-v2/admin/people/?institution=foo.com"
        let :result do
          client.get(url)
        end

        it "returns excaclty the people with the proper organization" do
          expect(result.status).to be == 200
          expect(result.body["people"].count).to be == 3 * 77

          expect_audit_entries("GET #{url}}", expected_audit_entries, 200, OPT_DISTINCT_CHANGE_AUDITS_ONLY)
        end
      end

      describe "get admin/people with pagination" do
        it "responses with 200" do
          resp1 = client.get("/api-v2/admin/people/?page=1&size=5")
          expect(resp1.status).to be == 200
          expect(resp1.body["data"].count).to be 5

          resp2 = client.get("/api-v2/admin/people/?page=2&size=5")
          expect(resp2.status).to be == 200
          expect(resp2.body["data"].count).to be 5
          expect(resp2.body["pagination"]).to be_a Hash
          expect(lists_of_maps_different?(resp1.body["data"], resp2.body["data"])).to eq true
        end

        it "responses with 200" do
          resp1 = client.get("/api-v2/admin/people/")
          expect(resp1.status).to be == 200
          expect(resp1.body["people"].count).to be 232
          expect(resp1.body["people"]).to be_a Array
        end
      end

      context "filter people by their subtype" do
        url = "/api-v2/admin/people/?page=1&size=100&subtype=Person&institution=foo.com"
        let :result do
          client.get(url)
        end

        it "returns excaclty the people with the proper sybtype" do
          expect(result.status).to be == 200
          expect(result.body["data"].count).to be == 77
          expect(
            result.body["data"].count { |p| p["subtype"] == "Person" }
          ).to be == 77

          expect_audit_entries("GET #{url}}", expected_audit_entries, 200, OPT_DISTINCT_CHANGE_AUDITS_ONLY)
        end
      end

      context "filter people by their subtype" do
        url = "/api-v2/admin/people/?subtype=Person&institution=foo.com"
        let :result do
          client.get(url)
        end

        it "returns excaclty the people with the proper sybtype" do
          expect(result.status).to be == 200
          # returns exactly 77
          expect(result.body["people"].count).to be == 77
          # all of those are of type Person
          expect(
            result.body["people"].count { |p| p["subtype"] == "Person" }
          ).to be == 77

          expect_audit_entries("GET #{url}}", expected_audit_entries, 200, OPT_DISTINCT_CHANGE_AUDITS_ONLY)
        end
      end
    end
  end
end
