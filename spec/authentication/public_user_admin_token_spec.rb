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

describe "Test status-code as public-user" do
  it "post responds with 403" do
    user_url = "/api/admin/full_text/"
    response = plain_faraday_json_client.post(user_url) do |req|
      req.body = {
        text: "string",
        media_resource_id: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
      }.to_json
      req.headers["Content-Type"] = "application/json"
    end
    expect(response.status).to be == 403
  end

  # TODO: Fix logic to return 401 instead of 403
  context "revoking the token " do
    {
      "/api/app-settings" => 200,
      "/api/context-keys/" => 200,
      "/api/contexts/" => 200,
      "/api/meta-keys/" => 200,
      "/api/media-entry/5feea8b4-fb56-4002-a119-a66608266d89" => 404,
      "/api/media-entries" => 200,
      "/api/roles/" => 200,
      "/api/vocabularies/" => 200,
      "/api/custom_urls/" => 200,
      "/api/full_texts/" => 200,
      "/api/usage-terms/" => 200,
      "/api/collections" => 200,
      "/api/keywords/?page=1&size=2" => 400, # FIXME
      "/api/groups/" => 200,

      "/api/workflows/" => 401,
      "/api/auth-info" => 401,
      "/api/edit_sessions/" => 401,

      "/api/admin/admins/" => 403,
      "/api/admin/app-settings" => 403,
      "/api/admin/context-keys/" => 403,
      "/api/admin/contexts/" => 403,
      "/api/admin/delegations/" => 403,
      "/api/admin/delegation/users/" => 403,
      "/api/admin/delegation/groups/" => 403,
      "/api/admin/edit_sessions/" => 403,
      "/api/admin/favorite/collections/" => 403,
      "/api/admin/favorite/media-entries/" => 403,
      "/api/admin/groups/" => 403,
      "/api/admin/io_interfaces/" => 403,
      "/api/admin/keywords/" => 403,
      "/api/admin/meta-keys/" => 403,
      "/api/admin/people/" => 403,
      "/api/admin/roles/?page=1&count=1" => 403,
      "/api/admin/usage-terms/" => 403,
      "/api/admin/users/" => 403,
      "/api/admin/static-pages/" => 403,
      "/api/admin/vocabularies/" => 403

    }.each do |url, code|
      it "accessing #{url}    results in expected status-code" do
        response = plain_faraday_json_client.get(url)
        expect(response.status).to eq(code)
      end
    end
  end
end

### TEST ENDPOINTS WITH MADEK-USER #################################

shared_context :test_proper_basic_auth do
  describe "Test status-code as madek-user " do
    it "against POST endpoints " do
      user_url = "/api/admin/full_text/"
      response = plain_faraday_json_client.post(user_url) do |req|
        req.body = {
          text: "string",
          media_resource_id: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
        }.to_json
        req.headers["Content-Type"] = "application/json"
      end
      expect(response.status).to be == 403
    end

    # TODO: Fix logic to return 401 instead of 403
    context "against GET endpoints " do
      {
        "/api/app-settings" => 200,
        "/api/context-keys/" => 200,
        "/api/contexts/" => 200,
        "/api/meta-keys/" => 200,
        "/api/media-entry/5feea8b4-fb56-4002-a119-a66608266d89" => 404,
        "/api/media-entries" => 200,
        "/api/roles/" => 200,
        "/api/vocabularies/" => 200,
        "/api/custom_urls/" => 200,
        "/api/full_texts/" => 200,
        "/api/usage-terms/" => 200,
        "/api/collections" => 200,
        "/api/keywords/?page=1&size=2" => 400, # FIXME
        "/api/groups/" => 200,

        "/api/workflows/" => 200,
        "/api/auth-info" => 200,
        "/api/edit_sessions/" => 200,

        "/api/admin/admins/" => 403,
        "/api/admin/app-settings" => 403,
        "/api/admin/context-keys/" => 403,
        "/api/admin/contexts/" => 403,
        "/api/admin/delegations/" => 403,
        "/api/admin/delegation/users/" => 403,
        "/api/admin/delegation/groups/" => 403,
        "/api/admin/edit_sessions/" => 403,
        "/api/admin/favorite/collections/" => 403,
        "/api/admin/favorite/media-entries/" => 403,
        "/api/admin/groups/" => 403,
        "/api/admin/io_interfaces/" => 403,
        "/api/admin/keywords/" => 403,
        "/api/admin/meta-keys/" => 403,
        "/api/admin/people/" => 403,
        "/api/admin/roles/?page=1&count=1" => 403,
        "/api/admin/usage-terms/" => 403,
        "/api/admin/users/" => 403,
        "/api/admin/static-pages/" => 403,
        "/api/admin/vocabularies/" => 403

      }.each do |url, code|
        it "accessing #{url}    results in expected status-code" do
          response = basic_auth_plain_faraday_json_client(@entity.login, @entity.password).get(url)
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
        plain_faraday_json_client.get("/api/auth-info")
      end

      it "responds with not authorized 401" do
        expect(response.status).to be == 401
      end
    end
  end

  context "Basic Authentication" do
    include_context :user_entity, :test_proper_basic_auth
  end
end

## TEST ENDPOINTS AS MADEK-ADMIN #################################

context "resource with admin auth" do
  include_context :json_client_for_authenticated_admin_user do
    describe "Test status-code as madek-user " do
      it "against POST endpoints " do
        # "/api/admin/full-text/" => 404, Post-Request
        user_url = "/api/admin/full_text/"
        response = plain_faraday_json_client.post(user_url) do |req|
          req.body = {
            text: "string",
            media_resource_id: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
          }.to_json
          req.headers["Content-Type"] = "application/json"
        end
        expect(response.status).to be == 403
      end

      # TODO: Fix logic to return 401 instead of 403
      context "against GET endpoints " do
        {
          "/api/app-settings" => 200,
          "/api/context-keys/" => 200,
          "/api/contexts/" => 200,
          "/api/meta-keys/" => 200,
          "/api/media-entry/5feea8b4-fb56-4002-a119-a66608266d89" => 404,
          "/api/media-entries" => 200,
          "/api/roles/" => 200,
          "/api/vocabularies/" => 200,
          "/api/custom_urls/" => 200,
          "/api/full_texts/" => 200,
          "/api/usage-terms/" => 200,
          "/api/collections" => 200,
          "/api/keywords/?page=1&size=2" => 400, # FIXME
          "/api/groups/" => 200,

          "/api/workflows/" => 200,
          "/api/auth-info" => 200,
          "/api/edit_sessions/" => 200,

          "/api/admin/admins/" => 200,
          "/api/admin/app-settings" => 200,
          "/api/admin/context-keys/" => 200,
          "/api/admin/contexts/" => 200,
          "/api/admin/delegations/" => 200,
          "/api/admin/delegation/users/" => 200,
          "/api/admin/delegation/groups/" => 200,
          "/api/admin/edit_sessions/" => 200,
          "/api/admin/favorite/collections/" => 200,
          "/api/admin/favorite/media-entries/" => 200,
          "/api/admin/groups/" => 200,
          "/api/admin/io_interfaces/" => 200,
          "/api/admin/keywords/" => 200,
          "/api/admin/meta-keys/" => 200,
          "/api/admin/people/" => 200,
          "/api/admin/roles/?page=1&count=1" => 200,
          "/api/admin/usage-terms/" => 200,
          "/api/admin/users/" => 200,
          "/api/admin/static-pages/" => 200,
          "/api/admin/vocabularies/" => 200

        }.each do |url, code|
          it "accessing #{url}    results in expected status-code" do
            response = client.get(url)
            expect(response.status).to eq(code)
          end
        end
      end
    end
  end
end

## TEST ENDPOINTS AS MADEK-TODKEN-USER #################################

context "resource with token-user auth" do
  let :user do
    FactoryBot.create :user, password: "TOPSECRET"
  end
  let :token do
    ApiToken.create user: user, scope_read: true,
      scope_write: true
  end

  describe "Test status-code as madek-user " do
    it "against POST endpoints " do
      user_url = "/api/admin/full_text/"
      response = newbasic_auth_plain_faraday_json_client(token.token, nil).post(user_url) do |req|
        req.body = {
          text: "string",
          media_resource_id: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
        }.to_json
        req.headers["Content-Type"] = "application/json"
      end
      expect(response.status).to be == 403
    end

    # TODO: Fix logic to return 401 instead of 403
    context "against GET endpoints " do
      {
        "/api/app-settings" => 200,
        "/api/context-keys/" => 200,
        "/api/contexts/" => 200,
        "/api/meta-keys/" => 200,
        "/api/media-entry/5feea8b4-fb56-4002-a119-a66608266d89" => 404,
        "/api/media-entries" => 200,
        "/api/roles/" => 200,
        "/api/vocabularies/" => 200,
        "/api/custom_urls/" => 200,
        "/api/full_texts/" => 200,
        "/api/usage-terms/" => 200,
        "/api/collections" => 200,
        "/api/keywords/?page=1&size=2" => 400, # FIXME
        "/api/groups/" => 200,

        "/api/workflows/" => 200,
        "/api/auth-info" => 200,
        "/api/edit_sessions/" => 200,

        "/api/admin/admins/" => 403,
        "/api/admin/app-settings" => 403,
        "/api/admin/context-keys/" => 403,
        "/api/admin/contexts/" => 403,
        "/api/admin/delegations/" => 403,
        "/api/admin/delegation/users/" => 403,
        "/api/admin/delegation/groups/" => 403,
        "/api/admin/edit_sessions/" => 403,
        "/api/admin/favorite/collections/" => 403,
        "/api/admin/favorite/media-entries/" => 403,
        "/api/admin/groups/" => 403,
        "/api/admin/io_interfaces/" => 403,
        "/api/admin/keywords/" => 403,
        "/api/admin/meta-keys/" => 403,
        "/api/admin/people/" => 403,
        "/api/admin/roles/?page=1&count=1" => 403,
        "/api/admin/usage-terms/" => 403,
        "/api/admin/users/" => 403,
        "/api/admin/static-pages/" => 403,
        "/api/admin/vocabularies/" => 403

      }.each do |url, code|
        it "accessing #{url}    results in expected status-code" do
          response = basic_auth_plain_faraday_json_client(token.token, nil).get(url)
          expect(response.status).to eq(code)
        end
      end
    end
  end
end
