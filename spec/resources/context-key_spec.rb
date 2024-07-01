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
    end

    # TODO test check data
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

# context "Getting context-keys with pagination" do
#   before :each do
#     @keywords = []
#     10.times do
#       # @keywords << FactoryBot.create(:keyword, external_uris: ["http://example.com"])
#       @keywords << FactoryBot.create( :context_key)
#     end
#   end
#
#   # let :plain_json_response do
#   #   plain_faraday_json_client.get("/api-v2/keywords/#{@keyword.id}")
#   # end
#
#   it "responses with 200" do
#     resp1 = plain_faraday_json_client.get("/api-v2/context-keys?page=0&size=5")
#
#     binding.pry
#
#     expect(resp1.status).to be == 200
#     expect(resp1.body.count).to be 5
#
#     resp2 = plain_faraday_json_client.get("/api-v2/context-keys?page=1&size=5")
#     expect(resp2.status).to be == 200
#     expect(resp2.body.count).to be 5
#
#     expect(lists_of_maps_different?(resp1.body, resp2.body)).to eq true
#   end
# end
