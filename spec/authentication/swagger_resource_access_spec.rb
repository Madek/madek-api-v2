require "spec_helper"

shared_context :user_entity do |ctx|
  context "for Database User" do
    before :each do
      @entity = FactoryBot.create :user, password: "TOPSECRET"
      @token = ApiToken.create user: @entity, scope_read: true,
                               scope_write: true
    end
    let :entity_type do
      "User"
    end
    include_context ctx if ctx
  end
end

shared_context :test_proper_basic_auth do
  describe "Test access to api-docs and endpoints" do
    context "with valid basicAuth-User (no rproxy-basicAuth)" do
      {
        "/api-v2/app-settings" => 200, # public endpoint
        "/api-v2/auth-info" => 200,
      }.each do |url, code|
        it "accessing #{url}    results in expected status-code" do
          response = new_token_auth_faraday_json_client(@token.token, url)
          expect(response.status).to eq(code)
        end
      end
    end

    context "with invalid db-basicAuth-User" do
      {
        "/api-v2/api-docs/index.html" => 200,
        "/api-v2/api-docs/index.css" => 200,
        "/api-v2/api-docs/swagger-ui.css" => 200,
        "/api-v2/api-docs/openapi.json" => 200
      }.each do |url, code|
        it "accessing #{url}    results in expected status-code" do
          response = new_token_auth_faraday_json_client("invalid-token", url)
          expect(response.status).to eq(code)
        end
      end

      {
        "/api-v2/app-settings" => 401, # public endpoint
        "/api-v2/auth-info" => 401
      }.each do |url, code|
        it "accessing #{url}    results in expected status-code" do
          response = new_token_auth_faraday_json_client("invalid-token", url)
          expect(response.status).to eq(code)
          expect(response.body["message"]).to eq("No token for this token-secret found!")
        end
      end
    end

    context "with public user" do
      {
        "/api-v2/app-settings" => 200, # public endpoint
        "/api-v2/api-docs/index.html" => 200,
        "/api-v2/api-docs/index.css" => 200,
        "/api-v2/api-docs/swagger-ui.css" => 200,
        "/api-v2/api-docs/openapi.json" => 200
      }.each do |url, code|
        it "accessing #{url}    results in expected status-code" do
          response = plain_faraday_json_client.get(url)

          expect(response.status).to eq(code)
          expect(response.headers["www-authenticate"]).to eq(nil)
        end
      end

      {
        "/api-v2/auth-info" => 401
      }.each do |url, code|
        it "accessing #{url}    results in expected status-code" do
          response = plain_faraday_json_client.get(url)

          expect(response.status).to eq(code)
          expect(response.body["message"]).to eq("Not authorized")
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
