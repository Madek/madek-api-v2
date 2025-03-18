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

describe "Access media-entries " do
  describe "with user-token" do
    include_context :json_client_for_authenticated_token_user

    context "GET requests to media_entries" do
      it "allows access to public media_entries" do
        url = "/api-v2/media-entries"
        response = wtoken_header_plain_faraday_json_client_get(token.token, url)

        expect(response.status).to eq(200)
        expect(response.body["media_entries"]).to be_an(Array)
      end

      it "denies access to admin media_entries" do
        url = "/api-v2/media-entries?page=1&size=5"
        response = wtoken_header_plain_faraday_json_client_get(token.token, url)

        expect(response.status).to eq(200)
        expect(response.body["data"]).to be_an(Array)
        expect(response.body["pagination"]).to be_a(Hash)
      end
    end

    context "GET requests to media_entries" do
      it "allows access to public media_entries" do
        url = "/api-v2/media-entries-related-data"
        response = wtoken_header_plain_faraday_json_client_get(token.token, url)

        expect(response.status).to eq(200)
        expect(response.body["media_entries"]).to be_an(Array)
      end
    end
  end

  describe "with admin-token" do
    include_context :json_client_for_authenticated_token_admin

    context "GET requests to media_entries" do
      it "allows access to admin media_entries" do
        url = "/api-v2/admin/media-entries"
        response = wtoken_header_plain_faraday_json_client_get(token.token, url)

        expect(response.status).to eq(200)
        expect(response.body["media_entries"]).to be_an(Array)
      end

      it "returns paginated data for admin media_entries" do
        url = "/api-v2/admin/media-entries?page=1&size=5"
        response = wtoken_header_plain_faraday_json_client_get(token.token, url)

        expect(response.status).to eq(200)
        expect(response.body["data"]).to be_an(Array)
        expect(response.body["pagination"]).to be_a(Hash)
      end
    end
  end
end
