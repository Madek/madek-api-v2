require "spec_helper"
require "shared/audit-validator"

context "Getting a keyword resource without authentication" do
  before :each do
    @keyword = FactoryBot.create(:keyword, external_uris: ["http://example.com"])
  end

  let :plain_json_response do
    plain_faraday_json_client.get("/api-v2/keywords/#{@keyword.id}")
  end

  it "responds with 200" do
    expect(plain_json_response.status).to be == 200
  end

  it "has the proper data" do
    keyword = plain_json_response.body
    expect(
      keyword.except("created_at", "updated_at")
    ).to eq(
      @keyword.attributes.with_indifferent_access
              .except(:creator_id, :created_at, :updated_at)
              .merge(external_uri: keyword["external_uris"].first)
    )
  end
end

context "Getting keywords by pagination" do
  before :each do
    @keywords = []
    10.times do
      @keywords << FactoryBot.create(:keyword, external_uris: ["http://example.com"])
    end
  end

  it "responses with 200" do
    resp1 = plain_faraday_json_client.get("/api-v2/keywords?page=1&size=5")
    expect(resp1.status).to be == 200
    expect(resp1.body["data"].count).to be 5

    resp2 = plain_faraday_json_client.get("/api-v2/keywords?page=2&size=5")
    expect(resp2.status).to be == 200
    expect(resp2.body["data"].count).to be 5

    expect(lists_of_maps_different?(resp1.body["data"], resp2.body["data"])).to eq true
  end

  it "responses with 200" do
    resp = plain_faraday_json_client.get("/api-v2/keywords")
    expect(resp.status).to be == 200
    expect(resp.body["keywords"].count).to be 10
  end
end
