require "spec_helper"
require "shared/audit-validator"
require Pathname(File.expand_path("..", __FILE__)).join("shared")
require Pathname(File.expand_path("../../media-entry", __FILE__)).join("shared")

def favorite_url(collection_id, user_id)
  "/api-v2/admin/favorites/collections/#{collection_id}/users/#{user_id}"
end

RSpec.describe "Admin::Favorites API", type: :request do
  include_context :setup_owner_user_for_token_access_base
  include_context :setup_both_for_token_access

  let(:base_url) { "/api-v2/admin/favorites/collections/" }

  describe "Admin user token operations for media-entry favorites" do
    before(:each) do
      response = wtoken_header_plain_faraday_json_client_get(admin_token.token, base_url)
      expect(response.status).to eq(200)
      expect(response.body.count).to eq(0)
    end

    it "returns a list containing the favorited media entry" do
      collection.favor_by(admin_user)
      response = wtoken_header_plain_faraday_json_client_get(admin_token.token, base_url)

      expect(response.status).to eq(200)
      expect(response.body.count).to eq(1)
    end

    it "creates a favorite via POST request" do
      collection.favor_by(admin_user)
      response = wtoken_header_plain_faraday_json_client_post(
        admin_token.token,
        favorite_url(collection.id, admin_user.id)
      )

      expect(response.status).to eq(200)
    end

    it "retrieves and then deletes a specific favorite" do
      collection.favor_by(admin_user)

      # retrieve
      response = wtoken_header_plain_faraday_json_client_get(
        admin_token.token,
        favorite_url(collection.id, admin_user.id)
      )
      expect(response.status).to eq(200)

      # delete
      response = wtoken_header_plain_faraday_json_client_delete(
        admin_token.token,
        favorite_url(collection.id, admin_user.id)
      )
      expect(response.status).to eq(200)

      # ensure it's gone
      response = wtoken_header_plain_faraday_json_client_get(
        admin_token.token,
        favorite_url(collection.id, admin_user.id)
      )
      expect(response.status).to eq(404)
    end
  end
end
