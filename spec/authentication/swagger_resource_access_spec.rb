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

shared_context :user_token_entity do |ctx|
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

shared_context :user_token_without_creds_entity do |ctx|
  context "for Database User" do
    before :each do
      @entity = FactoryBot.create :user, password: "TOPSECRET"
      @token = ApiToken.create user: @entity, scope_read: false,
        scope_write: false
    end
    let :entity_type do
      "User"
    end
    include_context ctx if ctx
  end
end

# ###################### BASIC-AUTH NOT SUPPORTED ANYMORE ####################################

shared_context :test_proper_public_user do
  describe "Test access to api-docs and endpoints" do
    context "with invalid db-token-User" do
      {
        "/api-v2/api-docs/index.html" => 200,
        "/api-v2/api-docs/index.css" => 200,
        "/api-v2/api-docs/swagger-ui.css" => 200,
        "/api-v2/api-docs/openapi.json" => 200
      }.each do |url, code|
        it "accessing #{url}    results in expected status-code" do
          response = wtoken_header_plain_faraday_json_client_get("Not-existing-user", url)
          expect(response.status).to eq(code)
        end
      end

      {
        "/api-v2/auth-info" => 401
      }.each do |url, code|
        it "accessing #{url}    results in expected status-code" do
          response = wtoken_header_plain_faraday_json_client_get("Not-existing-user", url)

          expect(response.status).to eq(code)
          expect(response.body["message"]).to eq("Access denied due invalid token")
        end
      end

      {
        "/api-v2/app-settings" => 401
        }.each do |url, code|
        it "accessing #{url}    results in expected status-code" do
          response = wtoken_header_plain_faraday_json_client_get("Not-existing-user", url)

          expect(response.status).to eq(code)
          expect(response.body["message"]).to eq("Access denied due invalid token")
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
    include_context :user_entity, :test_proper_public_user
  end
end

# ###################### TOKEN ###########################################################

shared_context :test_proper_user_token_auth do
  describe "Test access to api-docs and endpoints" do
    context "with valid token" do
      {
        "/api-v2/app-settings" => 200, # public endpoint
        "/api-v2/auth-info" => 200,

        "/api-v2/api-docs/index.html" => 200,
        "/api-v2/api-docs/index.css" => 200,
        "/api-v2/api-docs/swagger-ui.css" => 200,
        "/api-v2/api-docs/openapi.json" => 200
      }.each do |url, code|
        it "accessing #{url}    results in expected status-code" do
          response = wtoken_header_plain_faraday_json_client_get(@token.token, url)
          expect(response.status).to eq(code)
        end
      end
    end
  end
end

describe "/auth-info resource" do
  context "Access to api-docs" do
    include_context :user_token_entity, :test_proper_user_token_auth
  end
end

# ###################### TOKEN WITHOUT CREDS #############################################

shared_context :test_proper_user_token_without_creds_auth do
  describe "Test access to api-docs and endpoints" do
    context "with token without permissions" do
      {
        "/api-v2/app-settings" => 403, # public endpoint
        "/api-v2/auth-info" => 403,

        "/api-v2/api-docs/index.html" => 200,
        "/api-v2/api-docs/index.css" => 200,
        "/api-v2/api-docs/swagger-ui.css" => 200,
        "/api-v2/api-docs/openapi.json" => 200
      }.each do |url, code|
        it "accessing #{url}    results in expected status-code" do
          response = wtoken_header_plain_faraday_json_client_get(@token.token, url)
          expect(response.status).to eq(code)
        end
      end
    end
  end
end

describe "/auth-info resource" do
  context "Access to api-docs" do
    include_context :user_token_without_creds_entity, :test_proper_user_token_without_creds_auth
  end
end
