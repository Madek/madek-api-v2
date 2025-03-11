require "spec_helper"
require "uri"

context "Getting a person by person_id and without authentication" do
  before :each do
    @person = FactoryBot.create :person
  end

  let :plain_json_response do
    plain_faraday_json_client.get("/api-v2/people/#{@person.id}")
  end

  it "responds with 200" do
    expect(plain_json_response.status).to be == 200
  end
end

context "Getting a person by institution/institutional_id and without authentication" do
  before :each do
    institutional_id = Faker::Lorem.characters(number: 7)
    @person = FactoryBot.create(:person, institution: "local", institutional_id: institutional_id)
  end

  let :plain_json_response do
    key = [@person.institution, @person.institutional_id]
    encoded_key = URI.encode_www_form_component(key.to_json)

    plain_faraday_json_client.get("/api-v2/people/#{encoded_key}")
  end

  it "responds with 200" do
    expect(plain_json_response.status).to be == 200
  end
end
