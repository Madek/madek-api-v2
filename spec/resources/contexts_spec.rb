require "spec_helper"
require "cgi"
require "timecop"
require Pathname(File.expand_path("../../", __FILE__)).join("shared")

describe "Context Resource Access" do
  before :each do
    @keywords = Array.new(10) { FactoryBot.create(:context_key) }
  end

  context "Unauthenticated access to contexts" do
    let(:client) { plain_faraday_json_client }

    describe "Fetching all contextss" do
      let(:plain_json_response) { client.get("/api-v2/contexts") }

      it "returns 401 Unauthorized" do
        expect(plain_json_response.status).to eq(401)
      end
    end

    describe "Fetching a specific contexts" do
      let(:plain_json_response) do
        binding.pry
        client.get("/api-v2/contexts/#{@keywords.first.context_id}")
      end

      it "returns 401 Unauthorized" do
        expect(plain_json_response.status).to eq(401)
      end
    end
  end

  context "Authenticated access to protected contexts" do
    include_context :json_client_for_authenticated_token_user

    it "allows access to auth-info endpoint with a valid token" do
      response = client.get("/api-v2/auth-info")
      expect(response.status).to eq(200)
    end

    it "allows access to context resources with a valid token" do
      response = client.get("/api-v2/contexts")
      expect(response.status).to eq(200)

      context_id = response.body.first["id"]
      response = client.get("/api-v2/contexts/#{context_id}")
      expect(response.status).to eq(200)
      expect(response.body["id"]).to eq(context_id)
    end
  end
end
