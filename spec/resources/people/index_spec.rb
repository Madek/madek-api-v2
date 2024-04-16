require 'spec_helper'

context 'people' do

  before :each do
    @people = 77.times.map{
      FactoryBot.create :person,
      institution: 'foo.com'}
  end

  before :each do
    @people = 77.times.map{
      FactoryBot.create :people_group,
      institution: 'foo.com'
    }
  end

  before :each do
    @people = 77.times.map{
      FactoryBot.create :people_instgroup,
      institution: 'foo.com'}
  end




  context 'admin user' do
    include_context :json_client_for_authenticated_admin_user do

      describe 'get an unfiltered people list as an admin' do

        let :result do
          #client.get.relation('people').get()
          client.get("/api/admin/people/?count=100")
        end

        it 'responses with 200' do
          expect(result.status).to be== 200
        end

        it 'returns the count of requested items' do
          expect( result.body['people'].count).to be== 100
        end

      end

      context 'filter people by their institution' do

        let :result do
          client.get("/api/admin/people/?count=1000&institution=foo.com")
        end


        it 'returns excaclty the people with the proper oraganization' do
          expect(result.status).to be== 200
          expect(result.body['people'].count).to be== 3*77
        end

      end

      context 'filter people by their subtype' do

        let :result do
          client.get("/api/admin/people/?count=100&subtype=Person&institution=foo.com")
        end


        it 'returns excaclty the people with the proper sybtype' do
          expect(result.status).to be== 200
          # returns excaclty 77
          expect(result.body['people'].count).to be== 77
          # all of those are of type Person
          expect(
            result.body['people'].filter{|p| p['subtype']=='Person'}.count
          ).to be== 77
        end

      end

    end
  end
end


