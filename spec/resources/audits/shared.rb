require "spec_helper"

def remove_all_audits(obj = nil)
  AuditedRequest.delete_all
  AuditedResponse.delete_all
  AuditedChange.delete_all
  obj
end

shared_context :setup_owner_user_for_token_access_base do
  let(:owner) do
    owner = FactoryBot.create(:user, password: "owner", notes: "owner")
    remove_all_audits(owner)
  end

  let(:owner_token) do
    ot = ApiToken.create(user: owner, scope_read: true, scope_write: true, description: "owner_token")
    remove_all_audits(ot)
  end

  let(:user) do
    user = FactoryBot.create(:user, password: "password", notes: "user")
    FactoryBot.create :admin, user: user
    remove_all_audits(user)
  end

  let(:user_token) do
    ut = ApiToken.create(user: user, scope_read: true, scope_write: true, description: "token")
    remove_all_audits(ut)
  end

  let(:token) { user_token }
end

shared_context :setup_owner_user_for_token_access do
  include_context :setup_owner_user_for_token_access_base

  let(:media_entry) do
    me = FactoryBot.create(:media_entry, get_metadata_and_previews: false, responsible_user: owner)
    FactoryBot.create :media_file_for_image, media_entry: me
    remove_all_audits(me)
  end

  let(:media_file) do
    mf = FactoryBot.create(:media_file_for_image, media_entry: media_entry)
    remove_all_audits(mf)
  end
end
