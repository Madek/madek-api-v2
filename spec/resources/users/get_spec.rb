require 'spec_helper'

context 'users' do

  before :each do
    @user = FactoryBot.create :user
  end

  context 'non admin user' do
    include_context :json_client_for_authenticated_user do
      it 'is forbidden to retrieve any user' do
        expect(
          #client.get.relation('user').get(id: @user.id).response.status
          client.get("/api/users/#{CGI.escape(@user.id)}").status
        ).to be==403
      end
    end
  end

  context 'admin user' do
    include_context :json_client_for_authenticated_admin_user do

      context 'retriving a standard user' do
        let :get_user_result do
          #client.get.relation('user').get(id: @user.id)
          client.get("/api/users/#{CGI.escape(@user.id)}")
        end

        it 'works' do
          expect(get_user_result.status).to be==200
        end

        # TODO json roa remove: test links
        #it 'lets us navigate to the user itself via the self-relation' do
        #  expect(get_user_result.json_roa_data['self-relation']['href']).to match /#{@user.id}/
        #end

        it 'has the proper data, sans :searchable and :previous_id' do
          expect(get_user_result.body.with_indifferent_access \
              .slice(:id, :email, :login, :person_id)).to be== \
            @user.attributes.with_indifferent_access \
              .slice(:id, :email, :login, :person_id)
        end
      end

      context 'a deactivated user can\'t be retrieved' do
        let :deactivated_user do
          FactoryBot.create :user, is_deactivated: true
        end
        let :get_user_result do
          #client.get.relation('user').get(id: deactivated_user.id)
          client.get("/api/users/#{CGI.escape(deactivated_user.id)}")
        end

        it 'is not found' do
          expect(get_user_result.status).to be == 404
        end

      end

      context 'a user (with a naughty institutional_id)' do
        before :each do
          @inst_user = FactoryBot.create :user,
            institutional_id: '?this#id/needs/to/be/url&encoded'
        end
        it 'can be retrieved by the institutional_id' do
          expect(
            #client.get.relation('user').get(id: @inst_user.institutional_id).response.status
            client.get("/api/users/#{CGI.escape(@inst_user.institutional_id)}").status
          ).to be== 200
          expect(
            #client.get.relation('user').get(id: @inst_user.institutional_id).data["id"]
            client.get("/api/users/#{CGI.escape(@inst_user.institutional_id)}").body["id"]
          ).to be== @inst_user["id"]
        end
      end


      context 'a user (with a naughty email)' do
        before :each do
          valid_email_addresses  = [
            'Abc\@def@example.com',
            #TODO test users get by email
            #'Fred\ Bloggs@example.com', 
            'Joe.\\Blow@example.com',
            '"Abc@def"@example.com',
            #TODO test users get by email
            #'"Fred Bloggs"@example.com',
            'customer/department=shipping@example.com',
            '$A12345@example.com',
            '!def!xyz%abc@example.com',
            '_somename@example.com']
          @users= valid_email_addresses.map do |a|
            FactoryBot.create :user, email: a
          end
        end
        it 'can be retrieved by the email_address' do
          @users.each do |user|
            expect(
              #client.get.relation('user').get(id: user.email).response.status
              client.get("/api/users/#{CGI.escape(user.email)}").status
            ).to be== 200
            expect(
              #client.get.relation('user').get(id: user.email ).data["id"]
              client.get("/api/users/#{CGI.escape(user.email)}").body["id"]
            ).to be== user["id"]
          end
        end
      end

    end
  end
end
