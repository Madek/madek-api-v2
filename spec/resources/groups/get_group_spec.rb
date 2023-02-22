require 'spec_helper'

context 'groups' do

  before :each do
    @group = FactoryBot.create :group
    @user = FactoryBot.create :user
    @group.users << @user
  end

  context 'non admin user' do
    include_context :json_client_for_authenticated_user do
      it 'is forbidden to retrieve any group' do
        expect(
          #client.get.relation('group').get(id: @group.id).response.status
          client.get("/api/groups/", {id: @group.id}).status
        ).to be==403
      end
    end
  end

  context 'admin user' do
    include_context :json_client_for_authenticated_admin_user do

      context 'retriving a standard group' do
        let :get_group_result do
          #client.get.relation('group').get(id: @group.id)
          client.get("/api/groups/#{@group.id}")
        end

        it 'works' do
          expect(get_group_result.status).to be==200
        end

        # TODO json roa test links
        #it 'lets us navigate to the group itself via the self-relation' do
        #  expect(get_group_result.json_roa_data['self-relation']['href']).to match /#{@group.id}/
        #end

        it 'has the proper data, sans :searchable and :previous_id' do
          expect(get_group_result.body).to be== \
            @group.attributes.with_indifferent_access.except(:searchable, :previous_id)
        end
      end

      context 'a institunal group (with naughty institutional_id)' do
        before :each do
          @inst_group = FactoryBot.create :institutional_group,
            institutional_id: '?this#id/needs/to/be/url&encoded'
        end
        it 'can be retrieved by the institutional_id' do
          expect(
            #client.get.relation('group').get(id: @inst_group.institutional_id).response.status
            #client.get("/api/groups/", {id: CGI.escape(@inst_group.institutional_id)}).status
            client.get("/api/groups/#{CGI.escape(@inst_group.institutional_id)}").status
          ).to be== 200
          expect(
            #client.get.relation('group').get(id: @inst_group.institutional_id).data["id"]
            #client.get("/api/groups/", {id: CGI.escape(@inst_group.institutional_id)}).body["id"]
            client.get("/api/groups/#{CGI.escape(@inst_group.institutional_id)}").body["id"]
          ).to be== @inst_group["id"]
        end
      end

    end
  end
end


