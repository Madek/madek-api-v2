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

describe "Getting a media-entry resource with authentication" do
  include_context :setup_owner_user_for_token_access

  context :check_forbidden_without_required_permission do
    it "is forbidden 403" do
      response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/media-entry/#{media_entry.id}")
      expect(response.status).to eq 403
    end
  end

  describe "update resource-level permissions on media-entry by group-id" do
    include_context :setup_both_for_token_access

    before :each do
      remove_all_audits
    end

    it "allows user with permission to update resource-level permissions" do
      url = "#{api_base_url}/media-entry/#{media_entry.id}/perms/group/#{group.id}"
      grant_perm = wtoken_header_plain_faraday_json_client_post(owner_token.token, url, body: {
        get_metadata_and_previews: true,
        get_full_size: true,
        edit_metadata: true
      })
      expect(grant_perm.status).to eq 200

      read_response = wtoken_header_plain_faraday_json_client_get(owner_token.token, "/api-v2/media-entry/#{media_entry.id}")
      expect(read_response.status).to eq 200

      uurl = "#{api_base_url}/media-entry/#{media_entry.id}/perms/resources"
      payload = {
        get_metadata_and_previews: true,
        get_full_size: true,
        responsible_user_id: nil,
        responsible_delegation_id: delegation_with_user.id
      }

      update_response = wtoken_header_plain_faraday_json_client(owner_token.token).put(uurl) do |req|
        req.body = payload.to_json
        req.headers["Content-Type"] = "application/json"
      end

      expect(update_response.status).to eq(200)
      expect(update_response.body["responsible_user_id"]).to eq(nil)
      expect(update_response.body["responsible_delegation_id"]).to eq(delegation_with_user.id)
      expect(update_response.body["get_full_size"]).to eq(true)
      expect(update_response.body["get_metadata_and_previews"]).to eq(true)
    end
  end

  describe "update resource-level permissions on media-entry by group-id" do
    include_context :setup_both_for_token_access

    before :each do
      remove_all_audits
    end

    it "allows user with permission to update resource-level permissions" do
      url = "#{api_base_url}/media-entry/#{media_entry.id}/perms/group/#{group.id}"
      grant_perm = wtoken_header_plain_faraday_json_client_post(owner_token.token, url, body: {
        get_metadata_and_previews: true,
        get_full_size: true,
        edit_metadata: true
      })
      expect(grant_perm.status).to eq 200

      read_response = wtoken_header_plain_faraday_json_client_get(owner_token.token, "/api-v2/media-entry/#{media_entry.id}")
      expect(read_response.status).to eq 200

      uurl = "#{api_base_url}/media-entry/#{media_entry.id}/perms/resources"
      payload = {
        get_metadata_and_previews: true,
        get_full_size: true,
        responsible_user_id: nil,
        responsible_delegation_id: delegation_with_group.id
      }

      update_response = wtoken_header_plain_faraday_json_client(owner_token.token).put(uurl) do |req|
        req.body = payload.to_json
        req.headers["Content-Type"] = "application/json"
      end

      expect(update_response.status).to eq(200)
      expect(update_response.body["responsible_user_id"]).to eq(nil)
      expect(update_response.body["responsible_delegation_id"]).to eq(delegation_with_group.id)
      expect(update_response.body["get_full_size"]).to eq(true)
      expect(update_response.body["get_metadata_and_previews"]).to eq(true)
    end
  end
end
