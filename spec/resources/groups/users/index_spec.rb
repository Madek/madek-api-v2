require 'spec_helper'

describe 'getting the index of group-users' do

  before :each do
    @group = FactoryBot.create :institutional_group

    @group_users = 202.times.map do
      FactoryBot.create :user, institutional_id: SecureRandom.uuid
    end

    @group.users << @group_users

    @other_users = 12.times.map do
      FactoryBot.create :user, institutional_id: SecureRandom.uuid
    end

  end

  context 'admin user' do
    include_context :json_client_for_authenticated_admin_user do

      describe 'geting the group_users ' do

        let :group_users_result do
          client.get("/api/admin/groups/#{CGI.escape(@group.id)}/users/")
        end

        it 'works' do
          expect(group_users_result.status).to be== 200
        end

        it 'returns some data but less than created because we paginate' do
          expect(
            group_users_result.body['users'].count
          ).to be< @group_users.count
        end

      end
    end
  end
end
