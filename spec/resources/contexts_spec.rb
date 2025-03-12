require "spec_helper"
require "cgi"
require "timecop"
require Pathname(File.expand_path("../../", __FILE__)).join("shared")

context "Getting a context resource without authentication" do
  before :each do
    @keywords = []
    10.times do
      @keywords << FactoryBot.create(:context_key)
    end
  end

  context "public context-key" do

    let(:client) { plain_faraday_json_client }

    describe "query context-key (unauthenticated)" do
      let(:plain_json_response) do
        plain_faraday_json_client.get("/api-v2/contexts")
      end

      it "responds with 401 Unauthorized" do
        expect(plain_json_response.status).to eq(401)
      end
    end

    describe "query context-key (unauthenticated)" do
      let(:plain_json_response) do
        binding.pry
        plain_faraday_json_client.get("/api-v2/contexts/#{@keywords.first.context_id}")
      end

      it "responds with 401 Unauthorized" do
        expect(plain_json_response.status).to eq(401)
      end
    end
  end

  context "protected context-key" do

    include_context :json_client_for_authenticated_token_user do

      it "accesses protected resource with valid session cookie" do
        resp = client.get("/api-v2/auth-info")
        expect(resp.status).to eq(200)
      end

      it "accesses protected resource with valid session cookie" do
        resp = client.get("/api-v2/contexts")
        expect(resp.status).to eq(200)

        context_id=resp.body.first["id"]
        resp = client.get("/api-v2/contexts/#{context_id}")
        expect(resp.status).to eq(200)
        expect(resp.body["id"]).to eq(context_id)
      end
    end

  end
end