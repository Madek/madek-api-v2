require "spec_helper"

# 403..forbidden 405..method not allowed 401..unauthorized

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

### TEST ENDPOINTS AS PUBLIC-USER/ANONYMOUS #################################

describe "1) Test status-code as public-user" do
  it "post responds with 403" do
    user_url = "/api-v2/admin/full_text/"
    response = plain_faraday_json_client.post(user_url) do |req|
      req.body = {
        text: "string",
        media_resource_id: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
      }.to_json
      req.headers["Content-Type"] = "application/json"
    end
    expect(response.status).to be == 403
  end

  context "revoking the token " do
    {
      "/api-v2/app-settings" => 200,
      "/api-v2/context-keys" => 200,
      "/api-v2/contexts" => 200,
      "/api-v2/meta-keys" => 200,
      "/api-v2/media-entry/5feea8b4-fb56-4002-a119-a66608266d89" => 404,
      "/api-v2/media-entries?page=1&size=1" => 200,
      "/api-v2/roles?page=1&size=1" => 200,
      "/api-v2/vocabularies" => 200,
      "/api-v2/custom_urls" => 200,
      "/api-v2/full_texts?page=1&size=1" => 200,
      "/api-v2/usage-terms" => 200,
      "/api-v2/collections?page=1&size=1" => 200,
      "/api-v2/keywords?page=1&size=2" => 200,
      "/api-v2/groups" => 200,

      "/api-v2/workflows" => 401,
      "/api-v2/auth-info" => 401,
      "/api-v2/edit_sessions?page=1&size=1" => 401,

      "/api-v2/admin/admins" => 403,
      "/api-v2/admin/app-settings" => 403,
      "/api-v2/admin/context-keys" => 403,
      "/api-v2/admin/contexts" => 403,
      "/api-v2/admin/delegations" => 403,
      "/api-v2/admin/delegation/users" => 403,
      "/api-v2/admin/delegation/groups" => 403,
      "/api-v2/admin/edit_sessions" => 403,
      "/api-v2/admin/favorite/collections" => 403,
      "/api-v2/admin/favorite/media-entries" => 403,
      "/api-v2/admin/groups" => 403,
      "/api-v2/admin/io_interfaces" => 403,
      "/api-v2/admin/keywords" => 403,
      "/api-v2/admin/meta-keys" => 403,
      "/api-v2/admin/people" => 403,
      "/api-v2/admin/roles?page=1&size=1" => 403,
      "/api-v2/admin/usage-terms" => 403,
      "/api-v2/admin/users" => 403,
      "/api-v2/admin/static-pages" => 403,
      "/api-v2/admin/vocabularies" => 403

    }.each do |url, code|
      it "accessing #{url}    results in expected status-code" do
        response = plain_faraday_json_client.get(url)
        expect(response.status).to eq(code)
      end
    end
  end
end

### TEST ENDPOINTS WITH MADEK-USER ##########################################

shared_context :test_proper_public_user do
  describe "2) Test status-code as madek-user " do
    it "against POST endpoints " do
      user_url = "/api-v2/admin/full_text/"
      response = plain_faraday_json_client.post(user_url) do |req|
        req.body = {
          text: "string",
          media_resource_id: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
        }.to_json
        req.headers["Content-Type"] = "application/json"
      end
      expect(response.status).to be == 403
    end

    context "against GET endpoints " do
      {
        "/api-v2/app-settings" => 200,
        "/api-v2/context-keys" => 200,
        "/api-v2/contexts" => 200,
        "/api-v2/meta-keys" => 200,
        "/api-v2/media-entry/5feea8b4-fb56-4002-a119-a66608266d89" => 404,
        "/api-v2/media-entries?page=1&size=1" => 200,
        "/api-v2/roles?page=1&size=1" => 200,
        "/api-v2/vocabularies" => 200,
        "/api-v2/custom_urls" => 200,
        "/api-v2/full_texts?page=1&size=1" => 200,
        "/api-v2/usage-terms" => 200,
        "/api-v2/collections?page=1&size=1" => 200,
        "/api-v2/keywords?page=1&size=2" => 200, # FIXME
        "/api-v2/groups" => 200,

        "/api-v2/workflows" => 401,
        "/api-v2/auth-info" => 401,
        "/api-v2/edit_sessions?page=1&size=1" => 401,

        "/api-v2/admin/admins" => 403,
        "/api-v2/admin/app-settings" => 403,
        "/api-v2/admin/context-keys" => 403,
        "/api-v2/admin/contexts" => 403,
        "/api-v2/admin/delegations" => 403,
        "/api-v2/admin/delegation/users" => 403,
        "/api-v2/admin/delegation/groups" => 403,
        "/api-v2/admin/edit_sessions" => 403,
        "/api-v2/admin/favorite/collections" => 403,
        "/api-v2/admin/favorite/media-entries" => 403,
        "/api-v2/admin/groups" => 403,
        "/api-v2/admin/io_interfaces" => 403,
        "/api-v2/admin/keywords" => 403,
        "/api-v2/admin/meta-keys" => 403,
        "/api-v2/admin/people" => 403,
        "/api-v2/admin/roles?page=1&size=1" => 403,
        "/api-v2/admin/usage-terms" => 403,
        "/api-v2/admin/users" => 403,
        "/api-v2/admin/static-pages" => 403,
        "/api-v2/admin/vocabularies" => 403

      }.each do |url, code|
        it "accessing #{url}    results in expected status-code x1" do

          # binding.pry

          # puts "token.token0: #{token}"
          # puts "token.token1: #{token.token}"
          # puts "token.token2: #{@token.token}"

          # response = wtoken_header_plain_faraday_json_client(@token.token).get(url)
          # response = wtoken_header_plain_faraday_json_client_get(token.token, url)
          # response = wtoken_header_plain_faraday_json_client_get("", url)
          # response = plain_faraday_json_client("", url)
          response = plain_faraday_json_client.get(url)
          expect(response.status).to eq(code)
        end
      end
    end
  end
end

describe "/auth-info resource" do
  context "without any authentication" do
    context "via json" do
      let :response do
        plain_faraday_json_client.get("/api-v2/auth-info")
      end

      it "responds with not authorized 401" do
        expect(response.status).to be == 401
      end
    end
  end

  context "Basic Authentication" do
    include_context :user_entity, :test_proper_public_user
  end
end

## TEST ENDPOINTS AS MADEK-ADMIN ############################################

context "3) resource with admin auth" do
  describe "Test status-code as public-user " do
    include_context :json_client_for_authenticated_token_admin do

      # let(:token) { @token }

      it "against POST endpoints " do
        user_url = "/api-v2/admin/full_text/"
        response = plain_faraday_json_client.post(user_url) do |req|
          req.body = {
            text: "string",
            media_resource_id: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
          }.to_json
          req.headers["Content-Type"] = "application/json"
        end
        binding.pry
        expect(response.status).to be == 403
      end

      it "against POST endpoints as admin-user" do
        user_url = "/api-v2/admin/full_text/"
        # puts "token.token1: #{token.token}"
        # puts "token.token2: #{@token.token}"
        response = wtoken_header_plain_faraday_json_client_post(token.token, user_url, body: {
          text: "string",
          media_resource_id: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
        })
        expect(response.status).to be == 200
      end

      context "against GET endpoints " do
        {
          "/api-v2/app-settings" => 200,
          "/api-v2/context-keys" => 200,
          "/api-v2/contexts" => 200,
          "/api-v2/meta-keys" => 200,
          "/api-v2/media-entry/5feea8b4-fb56-4002-a119-a66608266d89" => 404,
          "/api-v2/media-entries?page=1&size=1" => 200,
          "/api-v2/roles?page=1&size=1" => 200,
          "/api-v2/vocabularies" => 200,
          "/api-v2/custom_urls" => 200,
          "/api-v2/full_texts?page=1&size=1" => 200,
          "/api-v2/usage-terms" => 200,
          "/api-v2/collections?page=1&size=1" => 200,
          "/api-v2/keywords?page=1&size=2" => 200,
          "/api-v2/groups" => 200,

          "/api-v2/workflows" => 200,
          "/api-v2/auth-info" => 200,
          "/api-v2/edit_sessions?page=1&size=1" => 200,

          "/api-v2/admin/admins" => 200,
          "/api-v2/admin/app-settings" => 200,
          "/api-v2/admin/context-keys" => 200,
          "/api-v2/admin/contexts" => 200,
          "/api-v2/admin/delegations" => 200,
          "/api-v2/admin/delegation/users" => 200,
          "/api-v2/admin/delegation/groups" => 200,
          "/api-v2/admin/edit_sessions" => 200,
          "/api-v2/admin/favorite/collections" => 200,
          "/api-v2/admin/favorite/media-entries" => 200,
          "/api-v2/admin/groups" => 200,
          "/api-v2/admin/io_interfaces" => 200,
          "/api-v2/admin/keywords" => 200,
          "/api-v2/admin/meta-keys" => 200,
          "/api-v2/admin/people" => 200,
          "/api-v2/admin/roles?page=1&size=1" => 200,
          "/api-v2/admin/usage-terms" => 200,
          "/api-v2/admin/users" => 200,
          "/api-v2/admin/static-pages" => 200,
          "/api-v2/admin/vocabularies" => 200

        }.each do |url, code|
          it "accessing #{url}    results in expected status-code" do
            response = wtoken_header_plain_faraday_json_client_get(token.token, url)
            expect(response.status).to eq(code)
          end
        end
      end
    end
  end
end

# TEST ENDPOINTS AS MADEK-TOKEN-USER, NOT ADMIN ############################

context "4) resource with token-user auth" do
  describe "Test status-code as madek-user " do
    include_context :json_client_for_authenticated_token_user do
      it "against POST endpoints " do
        user_url = "/api-v2/admin/full_text/"
        response = plain_faraday_json_client.post(user_url) do |req|
          req.body = {
            text: "string",
            media_resource_id: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
          }.to_json
          req.headers["Content-Type"] = "application/json"
        end
        expect(response.status).to be == 403
      end

      it "against POST endpoints " do
        user_url = "/api-v2/admin/full_text/"
        response = wtoken_header_plain_faraday_json_client_post(token.token, user_url, body: {
          text: "string",
          media_resource_id: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
        })
        expect(response.status).to be == 403
      end

      context "against GET endpoints " do
        {
          "/api-v2/app-settings" => 200,
          "/api-v2/context-keys" => 200,
          "/api-v2/contexts" => 200,
          "/api-v2/meta-keys" => 200,
          "/api-v2/media-entry/5feea8b4-fb56-4002-a119-a66608266d89" => 404,
          "/api-v2/media-entries?page=1&size=1" => 200,
          "/api-v2/roles?page=1&size=1" => 200,
          "/api-v2/vocabularies" => 200,
          "/api-v2/custom_urls" => 200,
          "/api-v2/full_texts?page=1&size=1" => 200,
          "/api-v2/usage-terms" => 200,
          "/api-v2/collections?page=1&size=1" => 200,
          "/api-v2/keywords?page=1&size=2" => 200,
          "/api-v2/groups" => 200,

          "/api-v2/workflows" => 200,
          "/api-v2/auth-info" => 200,
          "/api-v2/edit_sessions?page=1&size=1" => 200,

          "/api-v2/admin/admins" => 403,
          "/api-v2/admin/app-settings" => 403,
          "/api-v2/admin/context-keys" => 403,
          "/api-v2/admin/contexts" => 403,
          "/api-v2/admin/delegations" => 403,
          "/api-v2/admin/delegation/users" => 403,
          "/api-v2/admin/delegation/groups" => 403,
          "/api-v2/admin/edit_sessions" => 403,
          "/api-v2/admin/favorite/collections" => 403,
          "/api-v2/admin/favorite/media-entries" => 403,
          "/api-v2/admin/groups" => 403,
          "/api-v2/admin/io_interfaces" => 403,
          "/api-v2/admin/keywords" => 403,
          "/api-v2/admin/meta-keys" => 403,
          "/api-v2/admin/people" => 403,
          "/api-v2/admin/roles?page=1&size=1" => 403,
          "/api-v2/admin/usage-terms" => 403,
          "/api-v2/admin/users" => 403,
          "/api-v2/admin/static-pages" => 403,
          "/api-v2/admin/vocabularies" => 403

        }.each do |url, code|
          it "accessing #{url}    results in expected status-code" do
            response = wtoken_header_plain_faraday_json_client_get(token.token, url)
            expect(response.status).to eq(code)
          end
        end
      end
    end
  end
end

# TEST ENDPOINTS AS MADEK-TOKEN-ADMIN-USER #################################

context "5) resource with token-user auth with admin-credentials" do
  describe "Test status-code as madek-user " do
    include_context :json_client_for_authenticated_token_user do
      it "against POST endpoints " do
        user_url = "/api-v2/admin/full_text/"
        response = wtoken_header_plain_faraday_json_client_post(token.token, user_url, body: {
          text: "string",
          media_resource_id: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
        })

        expect(response.status).to be == 403
      end
    end
  end

  describe "Test status-code as madek-user " do
    include_context :json_client_for_authenticated_token_admin do
      it "against POST endpoints " do
        user_url = "/api-v2/admin/full_text/"
        response = plain_faraday_json_client.post(user_url) do |req|
          req.body = {
            text: "string",
            media_resource_id: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
          }.to_json
          req.headers["Content-Type"] = "application/json"
        end
        expect(response.status).to be == 403
      end

      it "against POST endpoints " do
        user_url = "/api-v2/admin/full_text/"
        response = wtoken_header_plain_faraday_json_client_post(token.token, user_url, body: {
          text: "string",
          media_resource_id: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
        })

        expect(response.status).to be == 200
      end

      it "against POST endpoints " do
        user_url = "/api-v2/admin/full_text/"
        response = wtoken_header_plain_faraday_json_client_post(token.token, user_url, body: {
          text: "string",
          media_resource_id: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
        })
        expect(response.status).to be == 200
      end

      context "against GET endpoints " do
        {
          "/api-v2/app-settings" => 200,
          "/api-v2/context-keys" => 200,
          "/api-v2/contexts" => 200,
          "/api-v2/meta-keys" => 200,
          "/api-v2/media-entry/5feea8b4-fb56-4002-a119-a66608266d89" => 404,
          "/api-v2/media-entries?page=1&size=1" => 200,
          "/api-v2/roles?page=1&size=1" => 200,
          "/api-v2/vocabularies" => 200,
          "/api-v2/custom_urls" => 200,
          "/api-v2/full_texts?page=1&size=1" => 200,
          "/api-v2/usage-terms" => 200,
          "/api-v2/collections?page=1&size=1" => 200,
          "/api-v2/keywords?page=1&size=2" => 200,
          "/api-v2/groups" => 200,

          "/api-v2/workflows" => 200,
          "/api-v2/auth-info" => 200,
          "/api-v2/edit_sessions?page=1&size=1" => 200,

          "/api-v2/admin/admins" => 200,
          "/api-v2/admin/app-settings" => 200,
          "/api-v2/admin/context-keys" => 200,
          "/api-v2/admin/contexts" => 200,
          "/api-v2/admin/delegations" => 200,
          "/api-v2/admin/delegation/users" => 200,
          "/api-v2/admin/delegation/groups" => 200,
          "/api-v2/admin/edit_sessions" => 200,
          "/api-v2/admin/favorite/collections" => 200,
          "/api-v2/admin/favorite/media-entries" => 200,
          "/api-v2/admin/groups" => 200,
          "/api-v2/admin/io_interfaces" => 200,
          "/api-v2/admin/keywords" => 200,
          "/api-v2/admin/meta-keys" => 200,
          "/api-v2/admin/people" => 200,
          "/api-v2/admin/roles?page=1&size=1" => 200,
          "/api-v2/admin/usage-terms" => 200,
          "/api-v2/admin/users" => 200,
          "/api-v2/admin/static-pages" => 200,
          "/api-v2/admin/vocabularies" => 200

        }.each do |url, code|
          it "accessing #{url}    results in expected status-code" do
            response = wtoken_header_plain_faraday_json_client_get(token.token, url)
            expect(response.status).to eq(code)
          end
        end
      end
    end
  end
end
