require "spec_helper"

shared_context :user_entity do |ctx|
  context "for Database User" do
    before :each do
      @entity = FactoryBot.create :user, password: "TOPSECRET"
    end
    let :entity_type do
      "User"
    end
    include_context ctx if ctx
  end
end

# FYI: (def RPROXY_BASIC_FEATURE_ENABLED? true)

shared_context :test_proper_basic_auth do
  describe "Test access to api-docs and endpoints" do
    context "with valid basicAuth-User" do
      {
        "/api-v2/app-settings" => 200, # public endpoint
        "/api-v2/auth-info" => 200,

        "/api-v2/api-docs/index.html" => 200,
        "/api-v2/api-docs/index.css" => 200,
        "/api-v2/api-docs/swagger-ui.css" => 200,
        "/api-v2/openapi.json" => 200
      }.each do |url, code|
        it "accessing #{url}    results in expected status-code" do
          response = basic_auth_plain_faraday_json_client(@entity.login, @entity.password).get(url)
          expect(response.status).to eq(code)
        end
      end
    end

    context "with invalid basicAuth-User (basicAuth-header from reverse-proxy)" do
      {
        "/api-v2/app-settings" => 401, # public endpoint
        "/api-v2/auth-info" => 401,

        "/api-v2/api-docs/index.html" => 401,
        "/api-v2/api-docs/index.css" => 401,
        "/api-v2/api-docs/swagger-ui.css" => 401,
        "/api-v2/openapi.json" => 401
      }.each do |url, code|
        it "accessing #{url}    results in expected status-code" do
          response = basic_auth_plain_faraday_json_client("Not-existing-user", "pw").get(url)

          expect(response.status).to eq(code)
          expect(response.body["message"]).to eq("Neither User nor ApiClient exists for {:login-or-email-address \"Not-existing-user\"}")
          expect(response.headers["www-authenticate"]).to eq("Basic realm=\"Madek ApiClient with password or User with token.\"")
        end
      end
    end

    context "with public user (protected by rproxy-basic-auth)" do
      {
        "/api-v2/app-settings" => 401, # public endpoint
        "/api-v2/auth-info" => 401,

        "/api-v2/api-docs/index.html" => 401,
        "/api-v2/api-docs/index.css" => 401,
        "/api-v2/api-docs/swagger-ui.css" => 401,
        "/api-v2/openapi.json" => 401
      }.each do |url, code|
        it "accessing #{url}    results in expected status-code" do
          response = plain_faraday_json_client.get(url)

          expect(response.status).to eq(code)
          expect(response.body["message"]).to eq("Not authorized")
          expect(response.headers["www-authenticate"]).to eq("Basic realm=\"Madek ApiClient with password or User with token.\"")
        end
      end
    end
  end
end

describe "/auth-info resource" do
  context "Access to api-docs" do
    include_context :user_entity, :test_proper_basic_auth
  end
end
