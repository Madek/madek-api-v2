require 'spec_helper'

context 'users' do

  before :each do
    @users = 201.times.map{FactoryBot.create :user}
  end

  context 'non admin user' do
    # TODO Frage definition API2: users can retrieve users or we need query user id by person id
    include_context :json_client_for_authenticated_user do
      it 'is forbidden to retrieve users' do
        expect(
          client.get('/api/users/').status
        ).to be==403
      end
    end
  end

  context 'admin user' do
    include_context :json_client_for_authenticated_admin_user do

      describe 'get users' do

        let :users_result do
          client.get("/api/admin/users/?count=100")
        end

        it 'responses with 200' do
          expect(users_result.status).to be== 200
        end

        it 'returns some data but less than created because we paginate' do
          expect(
            users_result.body['users'].count
          ).to be< @users.count
        end

        # TODO json roa remove: test collection get
        #it 'using the roa collection we can retrieve all users' do
        #  set_of_created_ids = Set.new(@users.map(&:id))
        #  set_of_retrieved_ids = Set.new(users_result.collection().map(&:get).map{|x| x.data['id']})
        #  expect(set_of_created_ids - set_of_retrieved_ids).to be_empty
        #end

        it 'omits deactivated users' do
          deactivated_user = @users.sample
          deactivated_user.update!(is_deactivated: true)

          #all_indexed_user_ids = Set.new(users_result.collection().map(&:get).map{|x| x.data['id'].to_s})
          rs = users_result.body["users"]
          all_indexed_user_ids = Set.new(rs.map{|x| x['id'].to_s})
          #all_indexed_user_ids.getUhu
          expect(all_indexed_user_ids).not_to include(deactivated_user.id.to_s)
        end

      end
    end
  end
end
