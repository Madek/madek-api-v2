require "spec_helper"
require "base64"

# Which entities can authenticate against api-v2, and by which method.

describe "Access by entity" do
  def client_with_headers(headers = {})
    Faraday.new(url: api_base_url, headers: {accept: "application/json"}.merge(headers)) do |conn|
      conn.response :json, content_type: /\bjson$/
      conn.adapter Faraday.default_adapter
    end
  end

  def basic_auth_header(username, password)
    {Authorization: "Basic #{Base64.strict_encode64("#{username}:#{password}")}"}
  end

  def session_cookie_header(user_session)
    {Cookie: "#{Madek::Constants::MADEK_SESSION_COOKIE_NAME}=#{user_session.token}"}
  end

  def create_session(user)
    UserSession.create!(user: user, auth_system: AuthSystem.first.presence,
      token_hash: "hashimotio", created_at: Time.now)
  end

  let(:user) { FactoryBot.create :user, password: "TOPSECRET" }
  let(:token) { ApiToken.create user: user, scope_read: true, scope_write: true }

  let(:response) { client.get("/api-v2/auth-info/") }

  context "anonymous" do
    let(:client) { client_with_headers }

    it "is not authenticated" do
      expect(response.status).to eq 401
    end

    it "can read public endpoints" do
      expect(client.get("/api-v2/vocabularies/").status).to eq 200
    end
  end

  context "user with api-token in the 'Authorization: token' header" do
    let(:client) { client_with_headers(Authorization: "token #{token.token}") }

    it "is authenticated as the token's user" do
      expect(response.status).to eq 200
      expect(response.body["type"]).to eq "User"
      expect(response.body["id"]).to eq user.id
      expect(response.body["authentication-method"]).to eq "Token"
    end

    context "with an unknown token" do
      let(:client) { client_with_headers(Authorization: "token not-a-valid-token") }

      it "is rejected" do
        expect(response.status).to eq 401
      end
    end

    context "with a read-only token" do
      let(:token) { ApiToken.create user: user, scope_read: true, scope_write: false }

      it "can read" do
        expect(response.status).to eq 200
      end

      it "can not use unsafe http verbs" do
        expect(client.post("/api-v2/test-csrf/").status).to eq 403
      end
    end

    context "with a write-only token" do
      let(:token) { ApiToken.create user: user, scope_read: false, scope_write: true }

      it "can not read" do
        expect(response.status).to eq 403
      end

      it "can use unsafe http verbs without csrf-token" do
        expect(client.post("/api-v2/test-csrf/").status).to eq 204
      end
    end

    context "of a deactivated user" do
      before { user.update_column(:active_until, 1.day.ago) }

      it "is rejected" do
        pending "active_until is not checked for token authentication"
        expect(response.status).to eq 401
      end
    end
  end

  context "user with session cookie" do
    let(:user_session) { create_session(user) }
    let(:client) { client_with_headers(session_cookie_header(user_session)) }

    it "is authenticated as the session's user" do
      expect(response.status).to eq 200
      expect(response.body["type"]).to eq "User"
      expect(response.body["id"]).to eq user.id
      expect(response.body["authentication-method"]).to eq "Session"
    end

    context "with an unknown session token" do
      let(:client) do
        client_with_headers(Cookie: "#{Madek::Constants::MADEK_SESSION_COOKIE_NAME}=not-a-valid-session")
      end

      it "is rejected" do
        expect(response.status).to eq 401
      end
    end

    context "of a deactivated user" do
      before { user.update_column(:active_until, 1.day.ago) }

      it "is rejected" do
        pending "active_until is not checked for session authentication"
        expect(response.status).to eq 401
      end
    end
  end

  context "session cookie and api-token sent together" do
    let(:other_user) { FactoryBot.create :user, password: "TOPSECRET" }
    let(:user_session) { create_session(other_user) }

    context "with a valid token" do
      let(:client) do
        client_with_headers(session_cookie_header(user_session)
          .merge(Authorization: "token #{token.token}"))
      end

      it "authenticates as the token's user" do
        expect(response.status).to eq 200
        expect(response.body["id"]).to eq user.id
        expect(response.body["authentication-method"]).to eq "Token"
      end
    end

    context "with an unknown token" do
      let(:client) do
        client_with_headers(session_cookie_header(user_session)
          .merge(Authorization: "token not-a-valid-token"))
      end

      it "is rejected" do
        expect(response.status).to eq 401
      end
    end
  end

  context "http basic authentication is not supported" do
    context "with user login and password" do
      let(:client) { client_with_headers(basic_auth_header(user.login, "TOPSECRET")) }

      it "is not authenticated" do
        expect(response.status).to eq 401
      end
    end

    context "with api-token as username" do
      let(:client) { client_with_headers(basic_auth_header(token.token, "")) }

      it "is not authenticated" do
        expect(response.status).to eq 401
      end
    end

    context "with api-token as password" do
      let(:client) { client_with_headers(basic_auth_header("nonsense", token.token)) }

      it "is not authenticated" do
        expect(response.status).to eq 401
      end
    end

    context "with api-client login and password" do
      let(:api_client) { FactoryBot.create :api_client, password: "TOPSECRET" }
      let(:client) { client_with_headers(basic_auth_header(api_client.login, "TOPSECRET")) }

      it "is not authenticated" do
        expect(response.status).to eq 401
      end
    end
  end

  context "admin privileges" do
    let(:admin) do
      FactoryBot.create(:user, password: "TOPSECRET").tap { |u| FactoryBot.create :admin, user: u }
    end
    let(:admin_token) { ApiToken.create user: admin, scope_read: true, scope_write: true }
    let(:admin_url) { "/api-v2/admin/users/" }

    it "are required for an admin endpoint via token" do
      expect(client_with_headers(Authorization: "token #{token.token}").get(admin_url).status).to eq 403
      expect(client_with_headers(Authorization: "token #{admin_token.token}").get(admin_url).status).to eq 200
    end

    it "are required for an admin endpoint via session" do
      expect(client_with_headers(session_cookie_header(create_session(user))).get(admin_url).status).to eq 403
      expect(client_with_headers(session_cookie_header(create_session(admin))).get(admin_url).status).to eq 200
    end
  end
end
