require "spec_helper"
require "shared/audit-validator"
require Pathname(File.expand_path("..", __FILE__)).join("shared")
require Pathname(File.expand_path("../../", __FILE__)).join("audits/shared")

describe "Getting a media-entry resource with authentication" do
  include_context :setup_owner_user_for_token_access

  describe "update resource-level permissions on media-entry by group-id" do
    include_context :setup_both_for_token_access

    before :each do
      remove_all_audits
    end

    it "allows user with permission to update resource-level permissions" do
      url = "#{api_base_url}/media-entries/#{media_entry.id}/perms/users/#{owner.id}"
      grant_perm = wtoken_header_plain_faraday_json_client_post(owner_token.token, url, body: {
        get_metadata_and_previews: true,
        get_full_size: true,
        edit_metadata: true,
        edit_permissions: true
      })
      expect(grant_perm.status).to eq 200

      read_response = wtoken_header_plain_faraday_json_client_get(owner_token.token, "/api-v2/media-entries/#{media_entry.id}")
      expect(read_response.status).to eq 200

      uurl = "#{api_base_url}/media-entries/#{media_entry.id}/perms/resources/"
      payload = {
        get_metadata_and_previews: true,
        get_full_size: true,
        responsible_user_id: owner.id,
        responsible_delegation_id: nil
      }

      update_response = wtoken_header_plain_faraday_json_client(owner_token.token).put(uurl) do |req|
        req.body = payload.to_json
        req.headers["Content-Type"] = "application/json"
      end

      expect(update_response.status).to eq(200)
      expect(update_response.body["responsible_user_id"]).to eq(owner.id)
      expect(update_response.body["responsible_delegation_id"]).to eq(nil)
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
      url = "#{api_base_url}/media-entries/#{media_entry.id}/perms/users/#{owner.id}"
      grant_perm = wtoken_header_plain_faraday_json_client_post(owner_token.token, url, body: {
        get_metadata_and_previews: true,
        get_full_size: true,
        edit_metadata: true,
        edit_permissions: true
      })
      expect(grant_perm.status).to eq 200

      read_response = wtoken_header_plain_faraday_json_client_get(owner_token.token, "/api-v2/media-entries/#{media_entry.id}")
      expect(read_response.status).to eq 200

      uurl = "#{api_base_url}/media-entries/#{media_entry.id}/perms/resources/"
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
