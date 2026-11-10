require "spec_helper"

# Access parity between the legacy API (`/api`, see ../api/src/madek/api) and
# api-v2. The specs in this directory encode the access decisions of the
# legacy API and check that api-v2 takes the same decision:
#
# - what the legacy API grants, api-v2 grants as well
# - what the legacy API denies, api-v2 denies as well
# - where the legacy API leaks data (documented in the single examples),
#   api-v2 has to stay at least as strict
#
# Legacy status codes (api/src/madek/api/resources.clj, utils/auth.clj):
# - media-resources and everything bound to one (meta-data, previews,
#   media-files) are readable when the resource is public, otherwise 401
#   without and 403 with an authenticated user lacking the permission
# - admin-only resources answer 403 for everybody but admins; the legacy API
#   even answers 403 without authentication, 401 is accepted as equal denial

module ApiAccessParity
  GRANTED = 200
  DENIED = [401, 403].freeze

  # Legacy `permission-by-auth-entity?`: public flag, responsible user,
  # responsible delegation (via user or group membership), user permission,
  # group permission. Admins are not privileged.
  GRANTING_CONDITIONS = %i[
    responsible_user
    responsible_delegation_member
    responsible_delegation_group_member
    user_permission
    group_permission
  ].freeze

  DENYING_CONDITIONS = %i[
    unrelated_user
    admin_without_permission
    user_permission_without_scope
    group_permission_without_scope
  ].freeze

  def anonymous_client
    plain_faraday_json_client
  end

  def client_for(user)
    token = ApiToken.create!(user: user, scope_read: true, scope_write: true)
    wtoken_header_plain_faraday_json_client(token.token)
  end

  def create_admin_user
    FactoryBot.create(:user).tap { |user| FactoryBot.create(:admin, user: user) }
  end

  # Returns a client acting under `condition` with regard to `resource`;
  # creates the users, groups, delegations and permissions this requires.
  # `scope` is the permission the legacy API checks for the requested url.
  def client_under(condition, resource, scope = :get_metadata_and_previews)
    case condition
    when :anonymous
      anonymous_client
    when :unrelated_user
      client_for(FactoryBot.create(:user))
    when :admin_without_permission
      client_for(create_admin_user)
    when :responsible_user
      client_for(resource.responsible_user)
    when :responsible_delegation_member
      user = FactoryBot.create(:user)
      delegation = FactoryBot.create(:delegation)
      delegation.users << user
      make_responsible(resource, delegation)
      client_for(user)
    when :responsible_delegation_group_member
      user = FactoryBot.create(:user)
      group = FactoryBot.create(:group)
      group.users << user
      delegation = FactoryBot.create(:delegation)
      delegation.groups << group
      make_responsible(resource, delegation)
      client_for(user)
    when :user_permission, :user_permission_without_scope
      user = FactoryBot.create(:user)
      grant(resource, scope, condition == :user_permission, user: user)
      client_for(user)
    when :group_permission, :group_permission_without_scope
      user = FactoryBot.create(:user)
      group = FactoryBot.create(:group)
      group.users << user
      grant(resource, scope, condition == :group_permission, group: group)
      client_for(user)
    else
      raise ArgumentError, "unknown condition #{condition}"
    end
  end

  def make_responsible(resource, delegation)
    resource.update_columns(responsible_user_id: nil, responsible_delegation_id: delegation.id)
  end

  # Permission rows never grant more than `scope`; `granted` false creates a
  # row that misses exactly `scope` (viewing stays granted for :get_full_size).
  def grant(resource, scope, granted, user: nil, group: nil)
    view = (scope == :get_metadata_and_previews) ? granted : true
    full_size = (scope == :get_full_size) && granted
    subject = user ? {user: user} : {group: group}
    case resource
    when MediaEntry
      factory = user ? :media_entry_user_permission : :media_entry_group_permission
      attrs = {media_entry: resource, get_metadata_and_previews: view, get_full_size: full_size, edit_metadata: false}
      attrs[:edit_permissions] = false if user
      FactoryBot.create(factory, **attrs, **subject)
    when Collection
      factory = user ? :collection_user_permission : :collection_group_permission
      attrs = {collection: resource, get_metadata_and_previews: view, edit_metadata_and_relations: false}
      attrs[:edit_permissions] = false if user
      FactoryBot.create(factory, **attrs, **subject)
    else
      raise ArgumentError, "can not grant permissions on #{resource.class}"
    end
  end

  def expect_legacy_status(response, expected)
    if expected == :denied
      expect(response.status).to be_in(DENIED)
    else
      expect(response.status).to eq(expected)
    end
  end
end

RSpec.configure do |config|
  config.include ApiAccessParity, file_path: %r{spec/api/}
end

# Single resource access as decided by the legacy `authorize-request-for-handler`
# and `media-files.authorization/authorize`.
#
# Requires `media_resource` (MediaEntry or Collection) and `path`.
# For scope :get_full_size the resource stays viewable for the public; only the
# full-size flag is withheld (legacy data-streams check both).
shared_examples :legacy_media_resource_access do |scope = :get_metadata_and_previews|
  def response_under(condition)
    client_under(condition, media_resource, legacy_scope).get(path)
  end

  let(:legacy_scope) { scope }

  context "when the resource is public" do
    before { media_resource.update_columns(:get_metadata_and_previews => true, scope => true) }

    %i[anonymous unrelated_user].each do |condition|
      it "grants access to #{condition}" do
        expect_legacy_status(response_under(condition), ApiAccessParity::GRANTED)
      end
    end
  end

  context "when the resource is not public" do
    before do
      media_resource.update_columns(get_metadata_and_previews: scope != :get_metadata_and_previews)
      media_resource.update_columns(get_full_size: false) if media_resource.has_attribute?(:get_full_size)
    end

    it "responds 401 to anonymous" do
      expect_legacy_status(response_under(:anonymous), 401)
    end

    ApiAccessParity::DENYING_CONDITIONS.each do |condition|
      it "responds 403 to #{condition}" do
        expect_legacy_status(response_under(condition), 403)
      end
    end

    ApiAccessParity::GRANTING_CONDITIONS.each do |condition|
      it "grants access to #{condition}" do
        expect_legacy_status(response_under(condition), ApiAccessParity::GRANTED)
      end
    end
  end
end

# Admin-only resources as decided by the legacy `wrap-authorize-admin!`.
# Requires `path`.
shared_examples :legacy_admin_only_access do
  it "denies anonymous" do
    expect_legacy_status(anonymous_client.get(path), :denied)
  end

  it "responds 403 to a user" do
    expect_legacy_status(client_for(FactoryBot.create(:user)).get(path), 403)
  end

  it "grants access to an admin" do
    expect_legacy_status(client_for(create_admin_user).get(path), ApiAccessParity::GRANTED)
  end
end

# Resources the legacy API serves to everybody. Requires `path`.
shared_examples :legacy_public_access do
  it "grants access to anonymous" do
    expect_legacy_status(anonymous_client.get(path), ApiAccessParity::GRANTED)
  end

  it "grants access to a user" do
    expect_legacy_status(client_for(FactoryBot.create(:user)).get(path), ApiAccessParity::GRANTED)
  end
end
