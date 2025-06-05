require "spec_helper"

shared_context :setup_owner_user_for_token_access_base do
  let!(:owner) { FactoryBot.create(:admin_user, password: "owner", notes: "owner") }
  let!(:owner_token) { ApiToken.create(user: owner, scope_read: true, scope_write: true, description: "owner_token") }

  let!(:user) { FactoryBot.create(:user, password: "password", notes: "user") }
  let!(:user_token) { ApiToken.create(user: user, scope_read: true, scope_write: true, description: "user_token") }

  let!(:admin_user) { FactoryBot.create(:admin_user, password: "password", notes: "admin") }
  let!(:admin_token) { ApiToken.create(user: admin_user, scope_read: true, scope_write: true, description: "admin_token") }

  let!(:collection) {
    FactoryBot.create :collection, creator: admin_user, responsible_user: admin_user
  }
end
