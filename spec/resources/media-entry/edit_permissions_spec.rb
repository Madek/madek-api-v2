require 'spec_helper'
require Pathname(File.expand_path('..', __FILE__)).join('shared')

  #TODO check can ... download, edit-metadata, edit-permissions
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
    it 'is forbidden 403' do
      expect(response.status).to be == 403
    end
  end

  context :check_allowed_if_responsible_user do
    before :example do
      url = "#{api_base_url}/media-entry/#{@media_entry.id}/perms/resources"
      update = newbasic_auth_plain_faraday_json_client(@owner.login, @owner.password)
        .put(url) do |req|
          req.body = {
            responsible_user_id: @entity.id
          }.to_json
          req.headers['Content-Type'] = 'application/json'  
        end
      #@media_entry.update! responsible_user: @entity
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
      url = "#{api_base_url}/media-entry/#{@media_entry.id}/perms/user/#{@entity.id}"
      user_perm = newbasic_auth_plain_faraday_json_client(@owner.login, @owner.password).post(url) do |req|
      
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

  context :check_not_allowed_if_updated_user_permission do
    before :example do
      curl = "#{api_base_url}/media-entry/#{@media_entry.id}/perms/user/#{@entity.id}"
      create_perm = newbasic_auth_plain_faraday_json_client(@owner.login, @owner.password).post(curl) do |req|      
        req.body = {
          get_metadata_and_previews: true,
          get_full_size: false,
          edit_metadata: false,
          edit_permissions: false,
        }.to_json
        req.headers['Content-Type'] = 'application/json'
      end
      expect(create_perm.status).to be == 200
      readok = newbasic_auth_plain_faraday_json_client(@entity.login, @entity.password)
        .get("/api/media-entry/#{@media_entry.id}")
      expect(readok.status).to be == 200

      uurl = "#{api_base_url}/media-entry/#{@media_entry.id}/perms/user/#{@entity.id}/get_metadata_and_previews/false"
      update_perm = newbasic_auth_plain_faraday_json_client(@owner.login, @owner.password).put(uurl)
      expect(update_perm.status).to be == 200
    end

    it 'is not allowed 403' do
      expect(response.status).to be == 403
    end
  end


  context :check_not_allowed_if_deleted_user_permission do
    before :example do
      curl = "#{api_base_url}/media-entry/#{@media_entry.id}/perms/user/#{@entity.id}"
      create_perm = newbasic_auth_plain_faraday_json_client(@owner.login, @owner.password).post(curl) do |req|      
        req.body = {
          get_metadata_and_previews: true,
          get_full_size: false,
          edit_metadata: false,
          edit_permissions: false,
        }.to_json
        req.headers['Content-Type'] = 'application/json'
      end
      expect(create_perm.status).to be == 200
      readok = newbasic_auth_plain_faraday_json_client(@entity.login, @entity.password)
        .get("/api/media-entry/#{@media_entry.id}")
      expect(readok.status).to be == 200

      uurl = "#{api_base_url}/media-entry/#{@media_entry.id}/perms/user/#{@entity.id}"
      del_perm = newbasic_auth_plain_faraday_json_client(@owner.login, @owner.password).delete(uurl)
      expect(del_perm.status).to be == 200
    end

    it 'is not allowed 403' do
      expect(response.status).to be == 403
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

  context :check_not_allowed_if_updated_group_permission do
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
      readok = newbasic_auth_plain_faraday_json_client(@entity.login, @entity.password)
        .get("/api/media-entry/#{@media_entry.id}")
      expect(readok.status).to be == 200

      uurl = "#{api_base_url}/media-entry/#{@media_entry.id}/perms/group/#{group.id}/get_metadata_and_previews/false"
      update_perm = newbasic_auth_plain_faraday_json_client(@owner.login, @owner.password).put(uurl)
      expect(update_perm.status).to be == 200      
    end

    it 'is not allowed 403' do
      expect(response.status).to be == 403
    end
  end

  context :check_not_allowed_if_deleted_group_permission do
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
      readok = newbasic_auth_plain_faraday_json_client(@entity.login, @entity.password)
        .get("/api/media-entry/#{@media_entry.id}")
      expect(readok.status).to be == 200

      uurl = "#{api_base_url}/media-entry/#{@media_entry.id}/perms/group/#{group.id}"
      update_perm = newbasic_auth_plain_faraday_json_client(@owner.login, @owner.password).delete(uurl)
      expect(update_perm.status).to be == 200      
    end

    it 'is not allowed 403' do
      expect(response.status).to be == 403
    end
   
  end

  context :check_download_allowed_if_user_permission do
    before :example do
      url = "#{api_base_url}/media-entry/#{@media_entry.id}/perms/user/#{@entity.id}"
      user_perm = newbasic_auth_plain_faraday_json_client(@owner.login, @owner.password).post(url) do |req|
        req.body = {
          get_metadata_and_previews: true,
          get_full_size: true,
          edit_metadata: true,
          edit_permissions: true,
        }.to_json
        req.headers['Content-Type'] = 'application/json'
      end
      expect(user_perm.status).to be == 200
      
    end

    it 'download is allowed 200' do
      expect(response.status).to be == 200
      uurl = "#{api_base_url}/media-entry/#{@media_entry.id}/media-file/data-stream"
      download = newbasic_auth_plain_faraday_json_client(@entity.login, @entity.password).get(uurl)
      expect(download.status).to be == 200
    end
  end

  context :check_edit_permissions_allowed_if_user_permission do
    before :example do
      url = "#{api_base_url}/media-entry/#{@media_entry.id}/perms/user/#{@entity.id}"
      user_perm = newbasic_auth_plain_faraday_json_client(@owner.login, @owner.password).post(url) do |req|
        req.body = {
          get_metadata_and_previews: true,
          get_full_size: true,
          edit_metadata: true,
          edit_permissions: true,
        }.to_json
        req.headers['Content-Type'] = 'application/json'
      end
      expect(user_perm.status).to be == 200
      
    end

    it 'edit resource perms is allowed 200' do
      expect(response.status).to be == 200
      uurl = "#{api_base_url}/media-entry/#{@media_entry.id}/perms/resource/get_metadata_and_previews/true"
      edit = newbasic_auth_plain_faraday_json_client(@entity.login, @entity.password).put(uurl)
      expect(edit.status).to be == 200
    end

    it 'edit user perms is allowed 200' do
      expect(response.status).to be == 200
      uurl = "#{api_base_url}/media-entry/#{@media_entry.id}/perms/user/#{@entity.id}/get_metadata_and_previews/true"
      edit = newbasic_auth_plain_faraday_json_client(@entity.login, @entity.password).put(uurl)
      expect(edit.status).to be == 200
    end

    it 'edit group perms is allowed 200' do
      #TODO
    end
  end

end
