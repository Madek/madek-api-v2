require "spec_helper"

shared_context :user_entity do |ctx|
  context "for Database User" do
    before :each do
      @entity = FactoryBot.create :user, password: "TOPSECRET"
    end

    let(:entity_type) { "User" }

    include_context ctx if ctx
  end
end

describe "Access edit_sessions " do
  describe "with user-token" do
    include_context :json_client_for_authenticated_token_user

    context "GET requests to edit sessions" do
      it "allows access to public edit sessions" do
        url = "/api-v2/edit_sessions/"
        response = wtoken_header_plain_faraday_json_client_get(token.token, url)

        expect(response.status).to eq(200)
        expect(response.body).to be_an(Array)
      end

      it "denies access to admin edit sessions" do
        url = "/api-v2/admin/edit_sessions/?page=1&size=5"
        response = wtoken_header_plain_faraday_json_client_get(token.token, url)

        expect(response.status).to eq(403)
        expect(response.body["message"]).to eq("The token has no admin-privileges.")
      end
    end
  end

  describe "with admin-token" do
    include_context :json_client_for_authenticated_token_admin

    context "GET requests to edit sessions" do
      it "allows access to admin edit sessions" do
        url = "/api-v2/admin/edit_sessions/"
        response = wtoken_header_plain_faraday_json_client_get(token.token, url)

        expect(response.status).to eq(200)
        expect(response.body).to be_an(Array)
      end

      it "returns paginated data for admin edit sessions" do
        url = "/api-v2/admin/edit_sessions/?page=1&size=5"
        response = wtoken_header_plain_faraday_json_client_get(token.token, url)

        expect(response.status).to eq(200)
        expect(response.body["data"]).to be_an(Array)
        expect(response.body["pagination"]).to be_a(Hash)
      end
    end
  end
end
