require "spec_helper"
require "shared/audit-validator"
require Pathname(File.expand_path("..", __FILE__)).join("shared")
require Pathname(File.expand_path("../../", __FILE__)).join("audits/shared")

shared_context :setup_owner_user_for_token_access_base do
  let!(:user) { FactoryBot.create(:admin_user, password: "password", notes: "user") }
  let!(:user_token) { ApiToken.create(user: user, scope_read: true, scope_write: true, description: "token") }
  let!(:token) { user_token }
end

shared_context :setup_both_for_token_access do
  include_context :setup_owner_user_for_token_access_base

  let(:group) { create(:group) }

  let(:collection) do
    collection =
      FactoryBot.create(:collection,
        get_metadata_and_previews: true)
    FactoryBot.create(:meta_datum_text,
      value: "gaura nitai bol",
      collection: collection)
    collection
  end
end

describe "Getting a media-entry resource with authentication" do
  include_context :setup_both_for_token_access

  context :check_forbidden_without_required_permission do
    it "creates, updates & delete group-permission for collection" do
      response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/collections/#{collection.id}")
      expect(response.status).to eq 200

      body = {
        "get_metadata_and_previews" => true,
        "edit_metadata_and_relations" => true
      }

      group2 = FactoryBot.create(:group)
      response = wtoken_header_plain_faraday_json_client_post(user_token.token, "/api-v2/collections/#{collection.id}/perms/groups/#{group2.id}", body: body)
      expect(response.status).to eq 200

      response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/collections/#{collection.id}/perms/groups/#{group2.id}")
      expect(response.status).to eq 200

      response = wtoken_header_plain_faraday_json_client_put(user_token.token, "/api-v2/collections/#{collection.id}/perms/groups/#{group2.id}/get_metadata_and_previews/false")
      expect(response.status).to eq 200
      expect(response.body["get_metadata_and_previews"]).to eq false

      response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/collections/#{collection.id}/perms/groups/")
      expect(response.status).to eq 200

      response = wtoken_header_plain_faraday_json_client_delete(user_token.token, "/api-v2/collections/#{collection.id}/perms/groups/#{group2.id}")
      expect(response.status).to eq 200
    end

    it "creates, updates & delete user-permission for collection" do
      response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/collections/#{collection.id}")
      expect(response.status).to eq 200

      body = {
        "get_metadata_and_previews" => true,
        "edit_metadata_and_relations" => true,
        "edit_permissions" => true
      }

      user2 = FactoryBot.create(:user)
      response = wtoken_header_plain_faraday_json_client_post(user_token.token, "/api-v2/collections/#{collection.id}/perms/users/#{user2.id}", body: body)
      expect(response.status).to eq 200

      response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/collections/#{collection.id}/perms/users/#{user2.id}")
      expect(response.status).to eq 200

      response = wtoken_header_plain_faraday_json_client_put(user_token.token, "/api-v2/collections/#{collection.id}/perms/users/#{user2.id}/get_metadata_and_previews/false")
      expect(response.status).to eq 200
      expect(response.body["get_metadata_and_previews"]).to eq false

      response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/collections/#{collection.id}/perms/users/")
      expect(response.status).to eq 200

      response = wtoken_header_plain_faraday_json_client_delete(user_token.token, "/api-v2/collections/#{collection.id}/perms/users/#{user2.id}")
      expect(response.status).to eq 200
    end

    it "get & updates resource-perms for collection" do
      response = wtoken_header_plain_faraday_json_client_get(user_token.token, "/api-v2/collections/#{collection.id}/perms/resources/")
      expect(response.status).to eq 200

      data = {
        "get_metadata_and_previews" => true,
        "responsible_user_id" => user.id,
        "clipboard_user_id" => nil,
        "workflow_id" => nil,
        "responsible_delegation_id" => nil
      }

      response = wtoken_header_plain_faraday_json_client_put(user_token.token, "/api-v2/collections/#{collection.id}/perms/resources/", body: data)
      expect(response.status).to eq 200

      response = wtoken_header_plain_faraday_json_client_put(user_token.token, "/api-v2/collections/#{collection.id}/perms/resources/get_metadata_and_previews/false")
      expect(response.status).to eq 200
      expect(response.body["get_metadata_and_previews"]).to eq false
    end
  end
end
