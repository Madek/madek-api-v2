require "spec_helper"

context "Getting a keyword resource without authentication" do
  before :each do
    @keyword = FactoryBot.create(:keyword, external_uris: ["http://example.com"])
  end

  let :plain_json_response do
    plain_faraday_json_client.get("/api/keywords/#{@keyword.id}")
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
