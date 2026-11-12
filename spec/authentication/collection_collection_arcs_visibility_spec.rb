require "spec_helper"

# Reading collection-collection arcs requires view rights on parent and child.

describe "Collection-collection arcs visibility" do
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
  let(:other_user) { FactoryBot.create :user }
  let(:anonymous_client) { json_client }

  let(:public_parent) { FactoryBot.create :collection, responsible_user: owner, get_metadata_and_previews: true }
  let(:public_child) { FactoryBot.create :collection, responsible_user: owner, get_metadata_and_previews: true }
  let(:private_child) { FactoryBot.create :collection, responsible_user: owner, get_metadata_and_previews: false }

  let!(:public_arc) { FactoryBot.create :collection_collection_arc, parent: public_parent, child: public_child }
  let!(:private_arc) { FactoryBot.create :collection_collection_arc, parent: public_parent, child: private_child }

  def listed_ids(client)
    response = client.get("/api-v2/collection-collection-arcs/", parent_id: public_parent.id)
    expect(response.status).to eq 200
    response.body["collection-collection-arcs"].map { |a| a["id"] }
  end

  describe "index" do
    it "lists an arc between public collections to anonymous" do
      expect(listed_ids(anonymous_client)).to include public_arc.id
    end

    it "does not list an arc with a private child to anonymous" do
      expect(listed_ids(anonymous_client)).not_to include private_arc.id
    end

    it "does not list an arc with a private child to another user" do
      expect(listed_ids(session_client(other_user))).not_to include private_arc.id
    end

    it "lists the arc with the private child to the responsible user" do
      expect(listed_ids(session_client(owner))).to include private_arc.id
    end
  end

  describe "GET /:id" do
    def get_arc(client, arc)
      client.get("/api-v2/collection-collection-arcs/#{arc.id}")
    end

    it "returns an arc between public collections to anonymous" do
      expect(get_arc(anonymous_client, public_arc).status).to eq 200
    end

    it "does not return an arc with a private child to anonymous" do
      expect(get_arc(anonymous_client, private_arc).status).to eq 404
    end

    it "does not return an arc with a private child to another user" do
      expect(get_arc(session_client(other_user), private_arc).status).to eq 404
    end

    it "returns the arc with the private child to the responsible user" do
      expect(get_arc(session_client(owner), private_arc).status).to eq 200
    end
  end

  describe "GET /collections/:parent_id/collection-arcs/:child_id" do
    def get_arc(client, arc)
      client.get("/api-v2/collections/#{arc.parent_id}/collection-arcs/#{arc.child_id}")
    end

    it "returns an arc between public collections to anonymous" do
      expect(get_arc(anonymous_client, public_arc).status).to eq 200
    end

    it "does not return an arc with a private child to anonymous" do
      expect(get_arc(anonymous_client, private_arc).status).to eq 401
    end

    it "does not return an arc with a private child to another user" do
      expect(get_arc(session_client(other_user), private_arc).status).to eq 403
    end

    it "returns the arc with the private child to the responsible user" do
      expect(get_arc(session_client(owner), private_arc).status).to eq 200
    end
  end
end
