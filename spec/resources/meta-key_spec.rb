require 'spec_helper'

context 'Getting a meta-key resource without authentication' do
  before :each do
    @meta_key = FactoryBot.create :meta_key_text
  end

  let :plain_json_response do
    plain_faraday_json_client.get("/api/meta-keys/#{@meta_key.id}")
  end

  it 'responds with 200' do
    expect(plain_json_response.status).to be == 200
  end
end
