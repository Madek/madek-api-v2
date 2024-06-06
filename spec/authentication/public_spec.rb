require "spec_helper"

describe "Test status-code as public-user" do

  # context "revoking the token " do
  #   ["/api/auth-info", "/api/app-settings"].each do |url|
  #     let(:url) { url }
  #
  #     it "accessing #{url} results in 401" do
  #       puts "Testing URL: #{url}"
  #       response = plain_faraday_json_client.get(url)
  #       expect(response.status).to eq(401)
  #     end
  #   end
  # end

  it "post responds with 403" do
    # "/api/admin/full-text/" => 404, Post-Request
    user_url = "/api/admin/full_text/"
    resonse = plain_faraday_json_client.post(user_url) do |req|
      req.body = {
        "text": "string",
        "media_resource_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
      }.to_json
      req.headers["Content-Type"] = "application/json"
    end
    expect(resonse.status).to be == 403
  end

  # 403..forbidden 301..moved permanently 404..not found 400..bad request 200..ok 405..method not allowed
  # 401..unauthorized
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
      # "/api/groups/" => 403, # FIXME: no auth required
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
      let(:url) { url }

      it "accessing #{url}    results in expected status-code" do
        puts "Testing URL: #{url}"
        response = plain_faraday_json_client.get(url)

        expect(response.status).to eq(code)
      end
    end
  end
end