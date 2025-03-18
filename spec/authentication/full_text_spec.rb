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

describe "Access full_texts " do
  describe "with user-token" do
    include_context :json_client_for_authenticated_token_user

    context "GET requests to full_texts" do
      it "allows access to full_texts without pagination" do
        url = "/api-v2/full_texts"
        response = wtoken_header_plain_faraday_json_client_get(token.token, url)

        expect(response.status).to eq(200)
        expect(response.body).to be_an(Array)
      end

      it "allows access to full_texts with pagination" do
        url = "/api-v2/full_texts?page=1&size=5"
        response = wtoken_header_plain_faraday_json_client_get(token.token, url)

        expect(response.status).to eq(200)
        expect(response.body["data"]).to be_an(Array)
        expect(response.body["pagination"]).to be_a(Hash)
      end
    end
  end
end
