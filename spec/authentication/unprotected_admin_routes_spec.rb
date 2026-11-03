require "spec_helper"

# Admin routes without the wrap-authorize-admin! middleware. Non-admin tokens
# are rejected by the token check on "/api-v2/admin/" URIs only; the examples
# for non-admin sessions and anonymous requests are pending until fixed.

describe "Unprotected admin routes" do
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

  let(:user) { FactoryBot.create :user }
  let(:admin) { FactoryBot.create(:user).tap { |u| FactoryBot.create :admin, user: u } }
  let(:user_token) { ApiToken.create user: user, scope_read: true, scope_write: true }

  let(:admin_client) { session_client(admin) }
  let(:user_session_client) { session_client(user) }
  let(:user_token_client) { json_client(Authorization: "token #{user_token.token}") }
  let(:anonymous_client) do
    json_client(Cookie: "madek-anti-csrf-token=#{csrf_token}", "x-csrf-token": csrf_token)
  end

  # Each context defines `send_request` (lambda taking a client) and
  # `unchanged` (lambda returning true while the request had no effect).
  shared_examples :admin_only do |with_admin_example: true|
    if with_admin_example
      it "succeeds for an admin session" do
        expect(send_request.call(admin_client).status).to eq 200
      end
    end

    it "is forbidden for a non-admin token" do
      expect(send_request.call(user_token_client).status).to eq 403
      expect(unchanged.call).to be true
    end

    it "is forbidden for a non-admin session" do
      pending "route lacks wrap-authorize-admin!"
      expect(send_request.call(user_session_client).status).to eq 403
      expect(unchanged.call).to be true
    end

    it "is forbidden for anonymous" do
      pending "route lacks wrap-authorize-admin!"
      expect(send_request.call(anonymous_client).status).to be_in [401, 403]
      expect(unchanged.call).to be true
    end
  end

  describe "GET /admin/delegations/:id" do
    let(:delegation) { FactoryBot.create :delegation }
    let(:send_request) { ->(client) { client.get("/api-v2/admin/delegations/#{delegation.id}") } }
    let(:unchanged) { -> { true } }

    include_examples :admin_only
  end

  describe "PUT /admin/delegations/:id" do
    let(:delegation) { FactoryBot.create :delegation, name: "original name" }
    let(:send_request) do
      lambda do |client|
        client.put("/api-v2/admin/delegations/#{delegation.id}", {name: "changed name"}.to_json)
      end
    end
    let(:unchanged) { -> { delegation.reload.name == "original name" } }

    include_examples :admin_only
  end

  describe "DELETE /admin/delegations/:id" do
    let(:delegation) { FactoryBot.create :delegation }
    let(:send_request) { ->(client) { client.delete("/api-v2/admin/delegations/#{delegation.id}") } }
    let(:unchanged) { -> { Delegation.exists?(delegation.id) } }

    include_examples :admin_only, with_admin_example: false
  end

  describe "PUT /admin/groups/:group-id/users/" do
    let(:group) { FactoryBot.create :group }
    let(:send_request) do
      lambda do |client|
        client.put("/api-v2/admin/groups/#{group.id}/users/", {users: [{id: user.id}]}.to_json)
      end
    end
    let(:unchanged) { -> { !group.reload.users.include?(user) } }

    include_examples :admin_only
  end

  describe "DELETE /admin/meta-keys/:id" do
    let(:meta_key) { FactoryBot.create :meta_key_text }
    let(:send_request) { ->(client) { client.delete("/api-v2/admin/meta-keys/#{meta_key.id}") } }
    let(:unchanged) { -> { MetaKey.exists?(meta_key.id) } }

    include_examples :admin_only
  end

  describe "DELETE /admin/roles/:id" do
    let(:role) { FactoryBot.create :role }
    let(:send_request) { ->(client) { client.delete("/api-v2/admin/roles/#{role.id}") } }
    let(:unchanged) { -> { Role.exists?(role.id) } }

    include_examples :admin_only
  end

  describe "GET /admin/people/:id" do
    let(:person) do
      FactoryBot.create :person, admin_comment: "internal note", identification_info: "internal id"
    end
    let(:send_request) { ->(client) { client.get("/api-v2/admin/people/#{person.id}") } }
    let(:unchanged) { -> { true } }

    include_examples :admin_only
  end
end
