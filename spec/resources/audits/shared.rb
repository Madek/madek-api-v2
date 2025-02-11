require "spec_helper"

def remove_all_audits
  AuditedRequest.delete_all
  AuditedResponse.delete_all
  AuditedChange.delete_all
end

shared_context :setup_owner_user_for_token_access_base do
  let(:owner) { FactoryBot.create(:user, password: "owner", notes: "owner") }
  let(:owner_token) { ApiToken.create(user: owner, scope_read: true, scope_write: true, description: "owner_token") }

  let(:user) {
    user = FactoryBot.create(:user, password: "password", notes: "user")
    FactoryBot.create :admin, user: user
    user
  }
  let(:user_token) { ApiToken.create(user: user, scope_read: true, scope_write: true, description: "token") }
  let(:token) { user_token }
end

shared_context :setup_owner_user_for_token_access do
  include_context :setup_owner_user_for_token_access_base

  let(:media_entry) do
    me = FactoryBot.create(:media_entry, get_metadata_and_previews: false, responsible_user: owner)
    FactoryBot.create :media_file_for_image, media_entry: me
    me
  end
  let(:media_file) { FactoryBot.create(:media_file_for_image, media_entry: media_entry) }
end
