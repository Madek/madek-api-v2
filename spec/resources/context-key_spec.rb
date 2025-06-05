require "spec_helper"

context "public context-key" do
  before :each do
    @context_key = FactoryBot.create :context_key
  end

  describe "query context-key" do
    let(:plain_json_response) do
      plain_faraday_json_client.get("/api-v2/context-keys/")
    end

    it "responds with 200" do
      expect(plain_json_response.status).to be == 200
      expect(plain_json_response.body).to be_a Array
      expect(plain_json_response.body.count).to be 1
    end

    it "has the proper data" do
      context_keys = plain_json_response.body
      expected_keys = @context_key.attributes.with_indifferent_access.except(:created_at, :updated_at)
      expect(context_keys.first.except("created_at", "updated_at", "admin_comment"))
        .to eq(expected_keys.except(:admin_comment))
    end
  end

  describe "query context-key" do
    let :plain_json_response do
      plain_faraday_json_client.get("/api-v2/context-keys/?page=1&size=5")
    end

    it "responds with 200" do
      expect(plain_json_response.status).to be == 200
      expect(plain_json_response.body["data"].count).to be 1
      expect(plain_json_response.body["data"]).to be_a Array
      expect(plain_json_response.body["pagination"]).to be_a Hash
    end
  end

  describe "get context-key" do
    it "has the proper data" do
      resp = plain_faraday_json_client.get("/api-v2/context-keys/")
      expect(resp.status).to eq(200)
      expect(resp.body.count).to eq(1)
    end
  end

  describe "get specific context-key" do
    let(:plain_json_response) do
      plain_faraday_json_client.get("/api-v2/context-keys/#{@context_key.id}")
    end

    it "responds with 200" do
      expect(plain_json_response.status).to eq(200)
    end

    it "has the proper data" do
      context_keys = plain_json_response.body
      expected_keys = @context_key.attributes.with_indifferent_access.except(:created_at, :updated_at)
      expect(context_keys.except("created_at", "updated_at", "admin_comment"))
        .to eq(expected_keys.except(:admin_comment))
    end
  end
end
