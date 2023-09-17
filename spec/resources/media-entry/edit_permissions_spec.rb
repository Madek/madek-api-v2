require 'spec_helper'
require Pathname(File.expand_path('..', __FILE__)).join('shared')

#TODO update and delete permissions
#TODO test edit auth
describe 'Getting a media-entry resource without authentication' do
  before :example do
    @media_entry = FactoryBot.create(:media_entry,
                                      get_metadata_and_previews: false)
  end

  shared_context :check_not_authenticated_without_public_permission do
    it 'is forbidden 401' do
      expect(response.status).to be == 401
    end
  end

  include_context :check_media_entry_resource_via_any,
                  :check_not_authenticated_without_public_permission
end

describe 'Getting a media-entry resource with authentication' do
  before :example do
    @owner = FactoryBot.create(:user, password: 'owner')
    @entity = FactoryBot.create(:user, password: 'password')
    @media_entry = FactoryBot.create(
      :media_entry, get_metadata_and_previews: false,
                    responsible_user:  @owner)
    
  end

  include_context :auth_media_entry_resource_via_plain_json
  
  context :check_forbidden_without_required_permission do
    before :example do
      user_perm = newbasic_auth_plain_faraday_json_client(@owner.login, @owner.password)
        .post("#{api_base_url}/media-entry/#{@media_entry.id}/perms/user/#{@entity.id}") do |req|
      
        req.body = {
          get_metadata_and_previews: false,
          get_full_size: false,
          edit_metadata: false,
          edit_permissions: false,
        }.to_json
        req.headers['Content-Type'] = 'application/json'
      end
      expect(user_perm.status).to be == 200
      #@media_entry.user_permissions << \
      #  FactoryBot.create(:media_entry_user_permission,
      #                     get_metadata_and_previews: false,
      #                     user: @entity)
      group = FactoryBot.create(:group)
      @entity.groups << group
      #@media_entry.group_permissions << \
      #  FactoryBot.create(:media_entry_group_permission,
      #                     get_metadata_and_previews: false,
      #                     group: group)
    end
    it 'is forbidden 403' do
      expect(response.status).to be == 403
    end
  end

  context :check_allowed_if_responsible_user do
    before :example do
      @media_entry.update! responsible_user: @entity
    end

    it 'is allowed 200' do
      expect(response.status).to be == 200
    end
  end

  context :check_allowed_if_user_belongs_to_responsible_delegation do
    before do
      delegation = create(:delegation)
      delegation.users << @entity
      @media_entry.update!(
        responsible_user: nil,
        responsible_delegation_id: delegation.id
      )
    end

    it 'is allowed 200' do
      expect(response.status).to be == 200
    end
  end

  context :check_allowed_if_user_belongs_to_group_belonging_to_responsible_delegation do
    before do
      delegation = create(:delegation)
      group = create(:group)
      delegation.groups << group
      group.users << @entity
      @media_entry.update!(
        responsible_user: nil,
        responsible_delegation_id: delegation.id
      )
    end

    it 'is allowed 200' do
      expect(response.status).to be == 200
    end
  end

  context :check_allowed_if_user_permission do
    before :example do
      user_perm = newbasic_auth_plain_faraday_json_client(@owner.login, @owner.password).post("#{api_base_url}/media-entry/#{@media_entry.id}/perms/user/#{@entity.id}") do |req|
      
        req.body = {
          get_metadata_and_previews: true,
          get_full_size: false,
          edit_metadata: false,
          edit_permissions: false,
        }.to_json
        req.headers['Content-Type'] = 'application/json'
      end
      expect(user_perm.status).to be == 200
      
    end

    it 'is allowed 200' do
      expect(response.status).to be == 200
    end
  end

  context :check_allowed_if_group_permission do
    let :group do
      group = FactoryBot.create(:group)
    end
    before :example do
      
      @entity.groups << group

      group_perm = newbasic_auth_plain_faraday_json_client(@owner.login, @owner.password).post("#{api_base_url}/media-entry/#{@media_entry.id}/perms/group/#{group.id}") do |req|
      
        req.body = {
          get_metadata_and_previews: true,
          get_full_size: false,
          edit_metadata: false
        }.to_json
        req.headers['Content-Type'] = 'application/json'
      end
      expect(group_perm.status).to be == 200
      
    end

    it 'is allowed 200' do
      expect(response.status).to be == 200
    end
   
  end
end
