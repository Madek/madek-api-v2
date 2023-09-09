require 'spec_helper'

context 'public context-key' do

  before :each do
    @context_key = FactoryBot.create :context_key
  end

  describe 'query context-key' do
    let :plain_json_response do
      plain_faraday_json_client.get("/api/context-keys/")
    end
  
    it 'responds with 200' do      
      expect(plain_json_response.status).to be == 200
    end
  
    # TODO test check data
  end

  describe 'get context-key' do
    let :plain_json_response do
      plain_faraday_json_client.get("/api/context-keys/#{@context_key.id}")
    end
  
    it 'responds with 200' do      
      expect(plain_json_response.status).to be == 200
    end
  
    # TODO test check data
  end
  
end
