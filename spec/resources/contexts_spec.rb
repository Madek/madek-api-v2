require "spec_helper"
require "cgi"
require "timecop"

describe "Context Resource Access" do
  before :each do
    @keywords = Array.new(10) { FactoryBot.create(:context_key) }
  end

  context "Unauthenticated access to contexts" do
    let(:client) { plain_faraday_json_client }

    describe "Fetching all contexts" do
      let(:plain_json_response) { client.get("/api-v2/contexts/") }

      it "allows access with 200" do
        expect(plain_json_response.status).to eq(200)
        expect(plain_json_response.body.count).to eq(10)
      end
    end

    describe "Fetching a specific contexts" do
      let(:plain_json_response) do
        client.get("/api-v2/contexts/#{@keywords.first.context_id}")
      end

      it "allows access with 200" do
        expect(plain_json_response.status).to eq(200)
        expect(plain_json_response.body["id"]).to be
      end
    end
  end

  context "Authenticated access to protected contexts" do
    include_context :json_client_for_authenticated_token_user

    it "allows access to auth-info endpoint with a valid token" do
      response = client.get("/api-v2/auth-info/")
      expect(response.status).to eq(200)
    end

    it "allows access to context resources with a valid token" do
      response = client.get("/api-v2/contexts/")
      expect(response.status).to eq(200)

      context_id = response.body.first["id"]
      response = client.get("/api-v2/contexts/#{context_id}")
      expect(response.status).to eq(200)
      expect(response.body["id"]).to eq(context_id)
    end
  end
end
