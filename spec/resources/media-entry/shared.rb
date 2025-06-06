require "spec_helper"

shared_context :media_entry_resource_via_plain_json do
  let :response do
    plain_faraday_json_client.get("/api-v2/media-entries/#{media_entry.id}")
  end
end

shared_context :auth_media_entry_resource_via_plain_json do
  let :response do
    wtoken_header_plain_faraday_json_client_get(token.token, "/api-v2/media-entries/#{media_entry.id}")
  end
end

shared_context :check_media_entry_resource_via_any do |ctx|
  context :via_plain_json do
    include_context :media_entry_resource_via_plain_json
    include_context ctx
  end
end

shared_context :setup_both_for_token_access do
  include_context :setup_owner_user_for_token_access_base

  let!(:another_user) { create(:user) }
  let(:group) { create(:group) }
  let(:delegation_with_user) { create(:delegation) }
  let(:delegation_with_group) { create(:delegation) }
  let!(:another_delegation) { create(:delegation) }

  before do
    group.users << user
    delegation_with_user.users << user

    group.users << owner
    delegation_with_user.users << owner

    delegation_with_group.groups << group
    another_delegation.users << another_user
  end

  let!(:media_entry) do
    me = FactoryBot.create(:media_entry, get_metadata_and_previews: false, responsible_user: owner)
    FactoryBot.create :media_file_for_image, media_entry: me
    me
  end

  let!(:media_file) { FactoryBot.create(:media_file_for_image, media_entry: media_entry) }
end
