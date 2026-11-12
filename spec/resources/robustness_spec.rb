require "spec_helper"

# Routes and filters that used to fail with server errors.

describe "Robustness of selected routes" do
  let(:csrf_token) { "ab63c8ab-a224-441d-9251-8068f6245252" }

  def json_client(headers = {})
    Faraday.new(url: api_base_url,
      headers: {accept: "application/json", "Content-Type": "application/json"}.merge(headers)) do |conn|
      conn.response :json, content_type: /\bjson$/
      conn.adapter Faraday.default_adapter
    end
  end

  def session_client(user)
    user_session = UserSession.create!(user: user, auth_system: AuthSystem.first.presence,
      token_hash: "hashimotio", created_at: Time.now)
    json_client(
      Cookie: "#{Madek::Constants::MADEK_SESSION_COOKIE_NAME}=#{user_session.token}; " \
              "madek-anti-csrf-token=#{csrf_token}",
      "x-csrf-token": csrf_token
    )
  end

  let(:owner) { FactoryBot.create :user }
  let(:owner_client) { session_client(owner) }

  describe "PUT /api-v2/admin/collections/:collection_id" do
    let(:collection) { FactoryBot.create :collection, responsible_user: owner, layout: "grid" }
    let(:url) { "/api-v2/admin/collections/#{collection.id}" }
    let(:admin_client) { session_client(FactoryBot.create(:admin).user) }

    it "updates the collection for an admin" do
      response = admin_client.put(url, {layout: "list"}.to_json)
      expect(response.status).to eq 200
      expect(collection.reload.layout).to eq "list"
    end

    it "responds 404 for an unknown collection" do
      response = admin_client.put("/api-v2/admin/collections/#{SecureRandom.uuid}", {layout: "list"}.to_json)
      expect(response.status).to eq 404
    end

    it "is forbidden for a non-admin owner" do
      expect(owner_client.put(url, {layout: "list"}.to_json).status).to eq 403
      expect(collection.reload.layout).to eq "grid"
    end
  end

  describe "GET /api-v2/media-entries/ with filter_by.media_entry" do
    let!(:media_entry) do
      FactoryBot.create :media_entry, responsible_user: owner, creator: owner,
        get_metadata_and_previews: true, is_published: true
    end
    let!(:other_media_entry) do
      FactoryBot.create :media_entry, get_metadata_and_previews: true, is_published: true
    end

    def listed_ids(filter)
      response = owner_client.get("/api-v2/media-entries/", filter_by: {media_entry: filter}.to_json)
      expect(response.status).to eq 200
      response.body["media_entries"].map { |me| me["id"] }
    end

    %w[responsible_user_id creator_id].each do |key|
      it "filters by #{key}" do
        ids = listed_ids(key => owner.id)
        expect(ids).to include media_entry.id
        expect(ids).not_to include other_media_entry.id
      end

      it "responds 422 for an invalid #{key}" do
        response = owner_client.get("/api-v2/media-entries/",
          filter_by: {media_entry: {key => "not-a-uuid"}}.to_json)
        expect(response.status).to eq 422
      end
    end
  end
end
