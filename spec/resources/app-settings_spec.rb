require 'spec_helper'

context 'Getting app-settings resource without authentication' do
  before :each do
    @keyword = FactoryBot.create(:app_setting)
  end

  let :plain_json_response do
    plain_faraday_json_client.get("/api/app-settings")
  end

  
  it 'responds with 200' do
    # TODO: roa: use plain json
    #expect(json_roa_keyword_resource.get.response.status)
    #  .to be == 200
    # TODO: roa: test header links
    #expect(
    #  json_roa_keyword_resource
    #    .get.relation('meta-key').get.response.status
    #).to be == 200
    #expect(
    #  json_roa_keyword_resource
    #    .get.relation('root').get.response.status
    #).to be == 200
    expect(plain_json_response.status).to be == 200
  end

  it 'has the proper data' do
    keyword = plain_json_response.body
    expect(
      keyword.except("created_at", "updated_at")
    ).to eq(
      @keyword.attributes.with_indifferent_access
        .except(  :created_at, :updated_at)
        
    )
  end
end
