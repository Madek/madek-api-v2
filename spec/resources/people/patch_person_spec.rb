require 'spec_helper'

context 'people' do

  before :each do
    @person = FactoryBot.create :person
  end

  context 'admin user' do
    include_context :json_client_for_authenticated_admin_user do

      describe 'patching/updating' do
        it 'works' do
          expect(
            client.put("/api/admin/people/#{CGI.escape(@person.id)}") do |req|
              req.body = {last_name: "new name"}.to_json
              req.headers['Content-Type'] = 'application/json'
            end.status
          ).to be== 200
        end

        it 'works when we do no changes' do
          expect(
            client.put("/api/admin/people/#{CGI.escape(@person.id)}") do |req|
              req.body = {last_name: @person.last_name}.to_json
              req.headers['Content-Type'] = 'application/json'
            end.status
          ).to be== 200
        end

        context 'patch result' do
          let :patch_result do
            #client.get.relation('person').patch(id: @person.id) do |req|
            client.put("/api/admin/people/#{CGI.escape(@person.id)}") do |req|
              req.body = {last_name: "new name"}.to_json
              req.headers['Content-Type'] = 'application/json'
            end
          end
          it 'contains the update' do
            expect(patch_result.body['last_name']).to be== 'new name'
          end
          
        end

      end


    end
  end
end
