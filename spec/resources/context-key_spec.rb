require "spec_helper"

context "public context-key" do
  # before :each do
  #   @context_key = FactoryBot.create(:context_key)
  # end

  before :each do
    @keywords = []
    10.times do
      @keywords << FactoryBot.create(:context_key)
    end

    @context_key = @keywords.first
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
      expected_keys = @context_key.attributes.with_indifferent_access.except(:created_at, :updated_at)
      expect(context_keys.first.except("created_at", "updated_at", "admin_comment"))
        .to eq(expected_keys.except(:admin_comment))
    end
  end

  describe "get context-key" do

    # before :each do
    #   @keywords = []
    #   10.times do
    #     @keywords << FactoryBot.create(:context_key)
    #   end
    # end

    it "has the proper data" do
      resp =       plain_faraday_json_client.get("/api-v2/context-keys")
      expect(resp.status).to eq(200)
      expect(resp.body.count).to eq(10)
    end

    # it "has the proper data" do
    #   resp =       plain_faraday_json_client.get("/api-v2/context-keys?page=1&size=5")
    #   expect(resp.status).to eq(200)
    #   expect(resp.body.count).to eq(5)
    #
    #   resp =       plain_faraday_json_client.get("/api-v2/context-keys?page=2&size=5")
    #   expect(resp.status).to eq(200)
    #   expect(resp.body.count).to eq(5)
    # end
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
