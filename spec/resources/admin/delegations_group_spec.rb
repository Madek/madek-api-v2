require "spec_helper"
require "shared/audit-validator"
require Pathname(File.expand_path("..", __FILE__)).join("shared")
require Pathname(File.expand_path("../../media-entry", __FILE__)).join("shared")

def delegation_url(group_id, delegation_id)
  "/api-v2/admin/delegations/groups/#{group_id}/delegations/#{delegation_id}"
end

RSpec.describe "Admin::Delegations API", type: :request do
  include_context :setup_owner_user_for_token_access_base
  include_context :setup_both_for_token_access

  let(:base_url) { "/api-v2/admin/delegations/groups/" }

  describe "GET /delegations without authentication" do
    it "returns 403 Forbidden" do
      response = plain_faraday_json_client.get(base_url)
      expect(response.status).to eq(403)
    end
  end

  describe "GET /delegations as regular user token" do
    it "returns 403 Forbidden" do
      response = wtoken_header_plain_faraday_json_client_get(user_token.token, base_url)
      expect(response.status).to eq(403)
    end
  end

  describe "Admin user token operations" do
    before(:each) do
      # Fetch existing delegations
      response = wtoken_header_plain_faraday_json_client_get(admin_token.token, base_url)
      expect(response.status).to eq(200)
      expect(response.body).not_to be_empty

      @group_id = response.body.first["group_id"]
      @delegation_id = response.body.first["delegation_id"]
    end

    it "lists all delegations" do
      # Already verified in before
      expect(true).to be_truthy
    end

    it "retrieves a specific delegation mapping" do
      response = wtoken_header_plain_faraday_json_client_get(admin_token.token,
        delegation_url(@group_id, @delegation_id))
      expect(response.status).to eq(200)
      expect(response.body.keys).to contain_exactly("group_id", "delegation_id")
    end

    it "create an already existing delegation mapping" do
      response = wtoken_header_plain_faraday_json_client_post(admin_token.token,
        delegation_url(@group_id, @delegation_id))
      expect(response.status).to eq(200)
      expect(response.body.keys).to contain_exactly("group_id", "delegation_id")
    end

    it "creates and then retrieves a new delegation mapping" do
      new_group_id = create(:group).id
      new_delegation_id = create(:delegation).id

      create_resp = wtoken_header_plain_faraday_json_client_post(
        admin_token.token,
        delegation_url(new_group_id, new_delegation_id)
      )

      expect(create_resp.status).to eq(200)
      expect(create_resp.body.keys).to contain_exactly("group_id", "delegation_id")

      get_resp = wtoken_header_plain_faraday_json_client_get(
        admin_token.token,
        delegation_url(new_group_id, new_delegation_id)
      )
      expect(get_resp.status).to eq(200)
      expect(get_resp.body.keys).to contain_exactly("group_id", "delegation_id")
    end

    it "deletes an existing delegation mapping" do
      delete_resp = wtoken_header_plain_faraday_json_client_delete(
        admin_token.token,
        delegation_url(@group_id, @delegation_id)
      )
      expect(delete_resp.status).to eq(200)
      expect(delete_resp.body.keys).to contain_exactly("group_id", "delegation_id")

      not_found_resp = wtoken_header_plain_faraday_json_client_get(
        admin_token.token,
        delegation_url(@group_id, @delegation_id)
      )
      expect(not_found_resp.status).to eq(404)
    end
  end
end
