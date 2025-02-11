require "spec_helper"

def remove_all_audits
  AuditedRequest.delete_all
  AuditedResponse.delete_all
  AuditedChange.delete_all
end

shared_context :setup_owner_user_for_token_access_base do
  let!(:owner) do
    FactoryBot.create(:user, password: "owner", notes: "owner")
  end

  let!(:owner_token) do
    ApiToken.create(user: owner, scope_read: true, scope_write: true, description: "owner-token")
  end

  let!(:user) do
    user = FactoryBot.create(:user, password: "password", notes: "user")
    FactoryBot.create :admin, user: user
    user
  end

  let!(:user_token) do
    ApiToken.create(user: user, scope_read: true, scope_write: true, description: "user-token")
  end

  let!(:admin_user) do
    FactoryBot.create(:admin_user, password: "password", notes: "user")
  end

  let!(:admin_user_token) do
    ApiToken.create(user: admin_user, scope_read: true, scope_write: true, description: "admin-user-token")
  end

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

  let!(:media_file) do
    FactoryBot.create(:media_file_for_image, media_entry: media_entry)
  end

  before(:each) { remove_all_audits }
end
