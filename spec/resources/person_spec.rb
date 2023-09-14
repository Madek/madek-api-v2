require 'spec_helper'

context 'Getting a person resource without authentication' do
  before :each do
    @person = FactoryBot.create :person
  end

  let :plain_json_response do
    plain_faraday_json_client.get("/api/people/#{@person.id}")
  end

  it 'responds with 200' do
    expect(plain_json_response.status).to be == 200
  end
end

