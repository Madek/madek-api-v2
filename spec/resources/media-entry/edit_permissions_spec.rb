require "spec_helper"
require "shared/audit-validator"
require Pathname(File.expand_path("..", __FILE__)).join("shared")
require Pathname(File.expand_path("../../", __FILE__)).join("audits/shared")

shared_context :setup_owner_user_for_token_access_base do
  let!(:owner) { FactoryBot.create(:user, password: "owner", notes: "owner") }
  let!(:owner_token) { ApiToken.create(user: owner, scope_read: true, scope_write: true, description: "owner_token") }

  let!(:user) { FactoryBot.create(:user, password: "password", notes: "user") }
  let!(:user_token) { ApiToken.create(user: user, scope_read: true, scope_write: true, description: "token") }
  let!(:token) { user_token }

  before(:each) { remove_all_audits }
end

shared_context :setup_owner_user_for_token_access do
  include_context :setup_owner_user_for_token_access_base

  let!(:media_entry) do
    me = FactoryBot.create(:media_entry, get_metadata_and_previews: false, responsible_user: owner)
    FactoryBot.create :media_file_for_image, media_entry: me
    me
  end

  let!(:media_file) { FactoryBot.create(:media_file_for_image, media_entry: media_entry) }
end

describe "Getting a media-entry resource without authentication" do
  shared_context :check_not_authenticated_without_public_permission do
    include_context :setup_owner_user_for_token_access

    context :via_plain_json do
      include_context :auth_media_entry_resource_via_plain_json
      it "is forbidden 401" do
        expect(response.status).to eq 403
      end
    end

    context :via_token do
      include_context :media_entry_resource_via_plain_json
      it "is forbidden 401" do
        expect(response.status).to eq 401
      end
    end
  end

  include_context :check_media_entry_resource_via_any, :check_not_authenticated_without_public_permission
end

describe "Getting a media-entry resource with authentication" do
  include_context :setup_owner_user_for_token_access

  context :check_forbidden_without_required_permission do
    it "is forbidden 403" do
      response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/media-entry/#{media_entry.id}")
      expect(response.status).to eq 403
    end
  end

  context :check_not_allowed_if_updated_user_permission do
    before :example do
      curl = "#{api_base_url}/media-entry/#{media_entry.id}/perms/user/#{user.id}"
      create_perm = wtoken_header_plain_faraday_json_client_post(owner_token.token, curl, body: {
        get_metadata_and_previews: true,
        get_full_size: false,
        edit_metadata: false,
        edit_permissions: false
      })
      expect(create_perm.status).to eq 200

      readok = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/media-entry/#{media_entry.id}")
      expect(readok.status).to eq 200
      uurl = "#{api_base_url}/media-entry/#{media_entry.id}/perms/user/#{user.id}/get_metadata_and_previews/false"
      update_perm = wtoken_header_plain_faraday_json_client_put(owner_token.token, uurl)
      expect(update_perm.status).to eq 200
    end

    it "is not allowed 403" do
      response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/media-entry/#{media_entry.id}")
      expect(response.status).to eq 403
    end
  end

  context :check_not_allowed_if_deleted_user_permission do
    before :example do
      curl = "#{api_base_url}/media-entry/#{media_entry.id}/perms/user/#{user.id}"
      create_perm = wtoken_header_plain_faraday_json_client_post(owner_token.token, curl, body: {
        get_metadata_and_previews: true,
        get_full_size: false,
        edit_metadata: false,
        edit_permissions: false
      })
      expect(create_perm.status).to eq 200

      readok = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/media-entry/#{media_entry.id}")
      expect(readok.status).to eq 200

      uurl = "#{api_base_url}/media-entry/#{media_entry.id}/perms/user/#{user.id}"
      del_perm = wtoken_header_plain_faraday_json_client_delete(owner_token.token, uurl)
      expect(del_perm.status).to eq 200
    end

    it "is not allowed 403" do
      response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/media-entry/#{media_entry.id}")
      expect(response.status).to eq 403
    end
  end

  context :check_allowed_if_group_permission do
    let(:group) { FactoryBot.create(:group) }

    before :example do
      user.groups << group
      url = "#{api_base_url}/media-entry/#{media_entry.id}/perms/group/#{group.id}"
      group_perm = wtoken_header_plain_faraday_json_client_post(owner_token.token, url, body: {
        get_metadata_and_previews: true,
        get_full_size: false,
        edit_metadata: false
      })
      expect(group_perm).not_to be_nil
    end

    it "is allowed 200" do
      response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/media-entry/#{media_entry.id}")
      expect(response.status).to eq 200
    end
  end

  context :check_not_allowed_if_updated_group_permission do
    let(:group) { FactoryBot.create(:group) }

    before :example do
      user.groups << group
      url = "#{api_base_url}/media-entry/#{media_entry.id}/perms/group/#{group.id}"
      group_perm = wtoken_header_plain_faraday_json_client_post(owner_token.token, url, body: {
        get_metadata_and_previews: true,
        get_full_size: false,
        edit_metadata: false
      })
      expect(group_perm.status).to eq 200
      readok = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/media-entry/#{media_entry.id}")
      expect(readok.status).to eq 200

      uurl = "#{api_base_url}/media-entry/#{media_entry.id}/perms/group/#{group.id}/get_metadata_and_previews/false"
      update_perm = token_header_plain_faraday_json_client(:put, uurl, owner_token.token)
      expect(update_perm).not_to be_nil
    end

    it "is not allowed 403" do
      response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/media-entry/#{media_entry.id}")
      expect(response.status).to eq 403
    end
  end

  context :check_not_allowed_if_deleted_group_permission do
    let(:group) { FactoryBot.create(:group) }

    before :example do
      user.groups << group
      url = "#{api_base_url}/media-entry/#{media_entry.id}/perms/group/#{group.id}"
      group_perm = wtoken_header_plain_faraday_json_client_post(owner_token.token, url, body: {
        get_metadata_and_previews: true,
        get_full_size: false,
        edit_metadata: false
      })
      expect(group_perm.status).to eq 200

      readok = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/media-entry/#{media_entry.id}")
      expect(readok.status).to eq 200

      uurl = "#{api_base_url}/media-entry/#{media_entry.id}/perms/group/#{group.id}"
      update_perm = wtoken_header_plain_faraday_json_client_delete(owner_token.token, uurl)
      expect(update_perm).not_to be_nil
      update_perm
    end

    it "is not allowed 403" do
      response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/media-entry/#{media_entry.id}")
      expect(response.status).to eq 403
    end
  end

  context :check_download_allowed_if_user_permission do
    before :example do
      url = "#{api_base_url}/media-entry/#{media_entry.id}/perms/user/#{user.id}"
      user_perm = wtoken_header_plain_faraday_json_client_post(owner_token.token, url, body: {
        get_metadata_and_previews: true,
        get_full_size: true,
        edit_metadata: true,
        edit_permissions: true
      })
      expect(user_perm.status).to eq 200
    end

    it "download is allowed 200" do
      response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/media-entry/#{media_entry.id}")
      expect(response.status).to eq 200
      uurl = "#{api_base_url}/media-entry/#{media_entry.id}/media-file/data-stream"
      download = wtoken_header_plain_faraday_json_client_get(user_token.token, uurl)
      expect(download.status).to eq 200
    end
  end

  context :check_edit_permissions_allowed_if_user_permission do
    before :example do
      url = "#{api_base_url}/media-entry/#{media_entry.id}/perms/user/#{user.id}"
      user_perm = wtoken_header_plain_faraday_json_client_post(owner_token.token, url, body: {
        get_metadata_and_previews: true,
        get_full_size: true,
        edit_metadata: true,
        edit_permissions: true
      })
      expect(user_perm.status).to eq 200
      expect_audit_entries_count(1, 26, 1)
    end

    before(:each) { remove_all_audits }

    it "edit resource perms is allowed 200" do
      expect_audit_entries_count(0, 0, 0)
      response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/media-entry/#{media_entry.id}")
      expect(response.status).to eq 200

      uurl = "#{api_base_url}/media-entry/#{media_entry.id}/perms/resource/get_metadata_and_previews/true"
      edit = wtoken_header_plain_faraday_json_client_put(user_token.token, uurl)
      expect(edit.status).to eq 200
      expect_audit_entries_count(1, 1, 1)
    end

    describe "For media_entry_user_permission " do
      it "edit user perms with new value is allowed 200" do
        expect_audit_entries_count(0, 0, 0)
        response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/media-entry/#{media_entry.id}")
        expect(response.status).to eq 200

        uurl = "#{api_base_url}/media-entry/#{media_entry.id}/perms/user/#{user.id}/get_metadata_and_previews/false"
        edit = wtoken_header_plain_faraday_json_client_put(user_token.token, uurl)
        expect(edit.status).to eq 200
        expect_audit_entries_count(1, 1, 1)
      end

      it "edit user perms with same value is allowed 200" do
        expect_audit_entries_count(0, 0, 0)
        response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/media-entry/#{media_entry.id}")
        expect(response.status).to eq 200

        uurl = "#{api_base_url}/media-entry/#{media_entry.id}/perms/user/#{user.id}/get_metadata_and_previews/true"
        edit = wtoken_header_plain_faraday_json_client_put(user_token.token, uurl)
        expect(edit.status).to eq 200
        expect_audit_entries_count(1, 0, 1)
      end
    end
  end
end
