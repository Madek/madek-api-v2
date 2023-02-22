require 'spec_helper'

context 'users' do

  before :each do
    @user = FactoryBot.create :user
  end

  context 'non admin user' do
    include_context :json_client_for_authenticated_user do
      it 'is forbidden to delete any user' do
        expect(
          client.delete("/api/users/#{@user.id}").status
        ).to be== 403
      end
    end
  end

  context 'admin user' do
    include_context :json_client_for_authenticated_admin_user do

      context 'deleting a standard user' do
        let :delete_user_result do
          #client.get.relation('user').delete(id: @user.id)
          client.delete("/api/users/#{CGI.escape(@user.id)}")
        end

        it 'returns the expected status code 204' do
          expect(delete_user_result.status).to be==204
        end

        it 'effectively removes the user' do
          expect(delete_user_result.status).to be==204
          expect(User.find_by(id: @user.id)).not_to be
        end

      end
    end
  end
end
