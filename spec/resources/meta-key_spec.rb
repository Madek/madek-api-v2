require "spec_helper"

context "Getting a meta-key resource without authentication" do
  before :each do
    @meta_key = FactoryBot.create :meta_key_text
  end

  describe "query meta-key" do
    let :plain_json_response do
      plain_faraday_json_client.get("/api-v2/meta-keys/#{@meta_key.id}")
    end

    it "responds with 200" do
      expect(plain_json_response.status).to be == 200
    end
  end
end

context "Getting meta-key's resource without authentication" do
  before :each do
    @count_of_core_fields = MetaKey.select("*").count
    @count_of_test_fields = 10
    @count_of_all_fields = @count_of_test_fields + @count_of_core_fields

    @keywords = []
    @count_of_test_fields.times do
      @keywords << FactoryBot.create(:meta_key_text,
        id: "test:#{Faker::Lorem.characters(number: 7)}")
    end
  end

  describe "query context-keys" do
    it "responds with 200" do
      resp = plain_faraday_json_client.get("/api-v2/meta-keys")
      expect(resp.status).to be == 200
      expect(resp.body["meta-keys"].count).to eq(@count_of_all_fields)
    end

    it "responds with 200" do
      resp1 = plain_faraday_json_client.get("/api-v2/meta-keys?page=1&size=5")
      expect(resp1.status).to be == 200
      expect(resp1.body["data"].count).to eq(5)

      resp2 = plain_faraday_json_client.get("/api-v2/meta-keys?page=2&size=5")
      expect(resp2.status).to be == 200
      expect(resp2.body["data"].count).to eq(5)
    end
  end
end
