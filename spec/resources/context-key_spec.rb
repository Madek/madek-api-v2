require "spec_helper"

context "public context-key" do
  before :each do
    @context_key = FactoryBot.create :context_key
  end

  describe "query context-key" do
    let :plain_json_response do
      plain_faraday_json_client.get("/api-v2/context-keys")
    end

    it "responds with 200" do
      expect(plain_json_response.status).to be == 200
      expect(plain_json_response.body).to be_a Array
      expect(plain_json_response.body.count).to be 1
    end
  end

  describe "query context-key" do
    let :plain_json_response do
      plain_faraday_json_client.get("/api-v2/context-keys?page=1&size=5")
    end

    it "responds with 200" do
      expect(plain_json_response.status).to be == 200
      expect(plain_json_response.body["data"].count).to be 1
      expect(plain_json_response.body["data"]).to be_a Array
      expect(plain_json_response.body["pagination"]).to be_a Hash
    end
  end

  describe "get context-key" do
    let :plain_json_response do
      plain_faraday_json_client.get("/api-v2/context-keys/#{@context_key.id}")
    end

    it "responds with 200" do
      expect(plain_json_response.status).to be == 200
    end

    # TODO test check data
  end
end
