require 'spec_helper'

context 'users' do

  context 'admin user' do

    include_context :json_client_for_authenticated_admin_user do

      before :each do
        @person = client.post("/api/people/") do |req|
          #client.get.relation('people').post do |req|
          req.body = { last_name: 'test',
                       subtype: 'Person'}.to_json
          req.headers['Content-Type'] = 'application/json'
        end.body.with_indifferent_access
      end

      describe 'creating' do


        describe 'a user' do

          it 'works' do
            expect( client.post("/api/users/") do |req|
              #client.get.relation('users').post do |req|
              req.body = {login: 'test',
                          person_id: @person[:id] }.to_json
              req.headers['Content-Type'] = 'application/json'
            end.status).to be== 201
          end
        end

      end

      describe 'a via post created user' do
        let :created_user do
          client.post("/api/users/") do |req|
          #client.get.relation('users').post do |req|
            req.body = {login: 'test',
                        person_id: @person[:id] }.to_json
            req.headers['Content-Type'] = 'application/json'
          end
        end
        describe 'the data' do
          it 'has the proper type' do
            expect(created_user.body['login']).to be== "test"
          end
        end
        # TODO json roa remove: test links
        #describe 'the json-roa-data' do
        #  it 'lets us navigate to the user via the self-relation' do
        #    expect(created_user.json_roa_data['self-relation']['href']).to \
        #      match /#{created_user.data['id']}/
        #  end
        #end
      end
    end
  end
end
