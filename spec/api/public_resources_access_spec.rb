require "spec_helper"
require Pathname(File.expand_path("..", __FILE__)).join("shared")

# Legacy relations: roles, role, person, delegation, auth-info and keywords
# (linked from meta-data); all readable without authentication except
# auth-info.

describe "Access parity with the legacy API: public resources" do
  describe "GET /roles/" do
    let(:path) { "/api-v2/roles/" }
    include_examples :legacy_public_access
  end

  describe "GET /roles/:id" do
    let(:path) { "/api-v2/roles/#{FactoryBot.create(:role).id}" }
    include_examples :legacy_public_access
  end

  describe "GET /keywords/:id" do
    let(:path) { "/api-v2/keywords/#{FactoryBot.create(:keyword).id}" }
    include_examples :legacy_public_access
  end

  describe "GET /people/:id" do
    let(:person) do
      FactoryBot.create :person, admin_comment: "internal note", identification_info: "internal id",
        institutional_id: SecureRandom.uuid
    end
    let(:path) { "/api-v2/people/#{person.id}" }

    include_examples :legacy_public_access

    # legacy people.clj `user-allowed-fields` (plus the derived `external_uri`)
    let(:legacy_fields) do
      %w[id first_name last_name pseudonym created_at updated_at subtype
        description external_uris external_uri institution]
    end

    it "exposes no more than the legacy fields to anonymous" do
      expect(anonymous_client.get(path).body.keys - legacy_fields).to be_empty
    end

    it "exposes no more than the legacy fields to a user" do
      expect(client_for(FactoryBot.create(:user)).get(path).body.keys - legacy_fields).to be_empty
    end
  end

  # Deliberate divergence: the legacy API serves `/delegations/:id` to
  # everybody (id, name and description). api-v2 serves delegations only on
  # `/admin/delegations/:id`; non-admins get no delegation data.
  describe "GET /delegations/:id (not public in api-v2)" do
    let(:delegation) { FactoryBot.create :delegation }

    it "is not served to anonymous" do
      expect(anonymous_client.get("/api-v2/delegations/#{delegation.id}").status).to be_in [401, 403, 404]
    end

    it "is not served to a user" do
      expect(client_for(FactoryBot.create(:user)).get("/api-v2/delegations/#{delegation.id}").status)
        .to be_in [401, 403, 404]
    end

    it "GET /admin/delegations/:id responds 403 to a user" do
      expect(client_for(FactoryBot.create(:user)).get("/api-v2/admin/delegations/#{delegation.id}").status).to eq 403
    end

    it "GET /admin/delegations/:id grants access to an admin" do
      expect(client_for(create_admin_user).get("/api-v2/admin/delegations/#{delegation.id}").status).to eq 200
    end
  end

  describe "GET /auth-info" do
    let(:path) { "/api-v2/auth-info/" }

    it "responds 401 to anonymous" do
      expect(anonymous_client.get(path).status).to eq 401
    end

    it "grants access to a user and describes that user" do
      user = FactoryBot.create(:user)
      response = client_for(user).get(path)
      expect(response.status).to eq ApiAccessParity::GRANTED
      expect(response.body["id"]).to eq user.id
    end
  end
end
