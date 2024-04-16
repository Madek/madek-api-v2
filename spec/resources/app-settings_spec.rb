require 'spec_helper'

context 'Getting app-settings resource without authentication' do

  before :each do
    @app_setting = FactoryBot.create(:app_setting)
  end

  let :plain_json_response do
    plain_faraday_json_client.get("/api/app-settings/")
  end

  it 'responds with 200' do
    expect(plain_json_response.status).to be == 200
  end

  it 'has the proper data' do
    app_setting = plain_json_response.body
    expect(
      app_setting.except("created_at", "updated_at")
    ).to eq(
      @app_setting.attributes.with_indifferent_access
      .except(:created_at, :updated_at))
  end

end
