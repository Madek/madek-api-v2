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

describe "Access users " do
  describe "with admin-token" do
    include_context :json_client_for_authenticated_token_admin

    context "GET requests to admin/users" do
      it "allows access without pagination" do
        url = "/api-v2/admin/users/"
        response = wtoken_header_plain_faraday_json_client_get(token.token, url)

        expect(response.status).to eq(200)
        expect(response.body["users"]).to be_an(Array)
      end

      it "allows access with pagination" do
        url = "/api-v2/admin/users/?page=1&size=5"
        response = wtoken_header_plain_faraday_json_client_get(token.token, url)

        expect(response.status).to eq(200)
        expect(response.body["data"]).to be_an(Array)
        expect(response.body["pagination"]).to be_a(Hash)
      end
    end

    context "GET requests to admin/users" do
      it "allows access without pagination" do
        url = "/api-v2/admin/users/"
        response = wtoken_header_plain_faraday_json_client_get(token.token, url)

        expect(response.status).to eq(200)
        expect(response.body["users"]).to be_an(Array)
      end

      it "allows access with pagination" do
        url = "/api-v2/admin/users/?page=1&size=5"
        response = wtoken_header_plain_faraday_json_client_get(token.token, url)

        expect(response.status).to eq(200)
        expect(response.body["data"]).to be_an(Array)
        expect(response.body["pagination"]).to be_a(Hash)
      end
    end
  end
end
