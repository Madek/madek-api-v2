require 'spec_helper'
require 'hashdiff'

context 'people' do

  before :each do
    @person = FactoryBot.create(:person, external_uris: ['http://example.com'])
    @person = @person.reload
  end

  context 'admin user' do
    include_context :json_client_for_authenticated_admin_user do

      context 'retriving a standard person' do
        let :get_person_result do
          client.get("/api/people/#{@person.id}")
        end

        it 'works' do
          expect(get_person_result.status).to be==200
        end

        it 'has the proper data' do
          person = get_person_result.body
          expect(
            person.with_indifferent_access.except(:created_at, :updated_at, :searchable)
          ).to eq(
            @person.attributes \
              .with_indifferent_access.except(:created_at, :updated_at, :searchable))
        end
      end

      context 'a institunal person (with naughty institutional_id)' do
        before :each do
          @inst_person = FactoryBot.create :people_instgroup ,
            institution: 'fake-university.com',
            institutional_id: 'https://fake-university.com/students/12345'
        end

        let :result do
          client.get(
            "/api/people/" +
            CGI.escape(['fake-university.com','https://fake-university.com/students/12345'].to_json))
        end

        it 'can be retrieved by the pair [institution, institutional_id]' do
          expect(result.status).to be== 200
          expect(result.body["id"]).to be== @inst_person["id"]
        end
      end

    end
  end
end
