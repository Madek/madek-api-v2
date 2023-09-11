require 'spec_helper'

context 'adding a user to a group via put' do

  before :each do
    @group = FactoryBot.create :institutional_group
    @user = FactoryBot.create :user, institutional_id: SecureRandom.uuid
  end

  it 'the user does not belong to the group' do
    expect(@group.users.reload.map(&:id)).not_to include(@user[:id])
  end


  context 'admin user' do
    include_context :json_client_for_authenticated_admin_user do


      describe 'adding the user to the group via put' do

        it 'responds with 200' do
          expect(
            client.put("/api/admin/groups/#{CGI.escape(@group.id)}/users/#{CGI.escape(@user.id)}").status
          ).to be== 200
        end

        it 'effectively adds the user to the group' do
          client.put("/api/admin/groups/#{CGI.escape(@group.id)}/users/#{CGI.escape(@user.id)}")
          expect(@group.users.reload.map(&:id)).to include(@user[:id])
        end

        it 'is indempotent' do
          client.put("/api/admin/groups/#{CGI.escape(@group.id)}/users/#{CGI.escape(@user.id)}")
          expect(@group.users.reload.map(&:id)).to include(@user[:id])
          expect(
            client.put("/api/admin/groups/#{CGI.escape(@group.id)}/users/#{CGI.escape(@user.id)}").status
          ).to be== 200
        end

      end

    end

  end
end
