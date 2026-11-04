require "spec_helper"

describe "CORS and session cookie" do
  let(:csrf_token) { "ab63c8ab-a224-441d-9251-8068f6245252" }
  let(:foreign_origin) { "https://evil.example.com" }
  let(:user) { FactoryBot.create :user, password: "TOPSECRET" }

  def client(headers = {})
    Faraday.new(url: api_base_url, headers: {accept: "application/json"}.merge(headers)) do |conn|
      conn.response :json, content_type: /\bjson$/
      conn.adapter Faraday.default_adapter
    end
  end

  def session_client(user)
    user_session = UserSession.create!(user: user, auth_system: AuthSystem.first.presence,
      token_hash: "hashimotio", created_at: Time.now)
    client(
      Cookie: "#{Madek::Constants::MADEK_SESSION_COOKIE_NAME}=#{user_session.token}; " \
              "madek-anti-csrf-token=#{csrf_token}",
      "x-csrf-token": csrf_token
    )
  end

  def sign_in(headers = {})
    client(headers).post("/sign-in") do |req|
      req.headers["Content-Type"] = "application/x-www-form-urlencoded"
      req.body = URI.encode_www_form(user: user.login, password: "TOPSECRET")
    end
  end

  def session_cookie(response)
    Array(response.headers["set-cookie"]).join("\n").split(/\n|,(?=\s*\S+=)/)
      .find { |c| c.strip.start_with?("#{Madek::Constants::MADEK_SESSION_COOKIE_NAME}=") }
  end

  describe "CORS" do
    it "does not allow a foreign origin" do
      response = session_client(user).get("/api-v2/auth-info/") { |req| req.headers["Origin"] = foreign_origin }
      expect(response.headers["access-control-allow-origin"]).to be_nil
      expect(response.headers["access-control-allow-credentials"]).to be_nil
    end

    it "does not allow a preflight from a foreign origin" do
      response = client.run_request(:options, "/api-v2/auth-info/", nil,
        "Origin" => foreign_origin, "Access-Control-Request-Method" => "GET")
      expect(response.headers["access-control-allow-origin"]).to be_nil
      expect(response.headers["access-control-allow-credentials"]).to be_nil
    end
  end

  describe "session cookie" do
    it "is HttpOnly and SameSite=Lax" do
      response = sign_in
      expect(response.status).to be_in [200, 302]
      cookie = session_cookie(response)
      expect(cookie).to be_present
      expect(cookie).to match(/HttpOnly/i)
      expect(cookie).to match(/SameSite=Lax/i)
      expect(cookie).not_to match(/;\s*Secure/i)
    end

    it "is Secure behind an https proxy" do
      response = sign_in("X-Forwarded-Proto" => "https")
      expect(response.status).to be_in [200, 302]
      expect(session_cookie(response)).to match(/;\s*Secure/i)
    end
  end

  describe "sign-out" do
    it "is not possible via GET" do
      expect(session_client(user).get("/api-v2/sign-out/").status).to be_in [404, 405]
    end

    it "works via POST" do
      expect(session_client(user).post("/api-v2/sign-out/").status).to eq 200
    end
  end
end
