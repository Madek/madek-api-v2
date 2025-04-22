require "spec_helper"
require "shared/audit-validator"

context "Testing coercion-erros by keywords" do
  before :each do
    @keywords = []
    10.times do
      @keywords << FactoryBot.create(:keyword, external_uris: ["http://example.com"])
    end
  end

  it "responses with 422 caused by coercion/spec-error" do
    resp = plain_faraday_json_client.get("/api-v2/keywords?page=abc")
    expect(resp.status).to be == 422
    expect(resp.body).to eq({"reason" => "Coercion-Error", "scope" => "request/query-params",
                                         "coercion-type" => "spec", "uri" => "GET /api-v2/keywords"})
  end

  it "responses with 200" do
    resp = plain_faraday_json_client.get("/api-v2/keywords/#{@keywords.first.id}")
    expect(resp.status).to be == 200
  end

  it "responses with 422 caused by coercion/schema-error" do
    resp = plain_faraday_json_client.get("/api-v2/keywords/abc-def")
    expect(resp.status).to be == 422
    expect(resp.body).to eq({"reason" => "Coercion-Error", "scope" => "request/path-params",
                                         "coercion-type" => "schema", "uri" => "GET /api-v2/keywords/abc-def"})
  end
end
