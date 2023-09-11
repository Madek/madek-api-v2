require 'spec_helper'

context 'users' do

  context 'admin user' do

    include_context :json_client_for_authenticated_admin_user do

      before :each do
        @person = client.post("/api/admin/people/") do |req|
          req.body = { last_name: 'test',
                       subtype: 'Person'}.to_json
          req.headers['Content-Type'] = 'application/json'
        end.body.with_indifferent_access
      end

      describe 'creating' do


        describe 'a user' do

          it 'works' do
            expect( client.post("/api/admin/users/") do |req|
              req.body = {login: 'test',
                          person_id: @person[:id] }.to_json
              req.headers['Content-Type'] = 'application/json'
            end.status).to be== 201
          end
        end

      end

      describe 'a via post created user' do
        let :created_user do
          client.post("/api/admin/users/") do |req|
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

      end
    end
  end
end
