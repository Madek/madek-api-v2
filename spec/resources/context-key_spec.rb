require "spec_helper"

context "public context-key" do
  before :each do
    @context_key = FactoryBot.create(:context_key)
  end

  describe "query context-key" do
    let(:plain_json_response) do
      plain_faraday_json_client.get("/api-v2/context-keys")
    end

    it "responds with 200" do
      expect(plain_json_response.status).to eq(200)
    end

    it "has the proper data" do
      context_keys = plain_json_response.body
      binding.pry
      expected_keys = @context_key.attributes.with_indifferent_access.except(:created_at, :updated_at)
      expect(context_keys.first.except("created_at", "updated_at", "admin_comment"))
        .to eq(expected_keys.except(:admin_comment))
    end
  end

  describe "get context-key" do
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
