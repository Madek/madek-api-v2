require "spec_helper"
require Pathname(File.expand_path("..", __FILE__)).join("shared")

# Legacy relations: group, groups, user, users, people (index).
#
# The legacy API serves these to admins only (`wrap-authorize-admin!`).
# api-v2 must not open them to non-admins on its admin routes.

describe "Access parity with the legacy API: admin-only resources" do
  let(:group) { FactoryBot.create :group }
  let(:user) { FactoryBot.create :user }

  {
    "GET /admin/groups/" => -> { "/api-v2/admin/groups/" },
    "GET /admin/groups/:id" => -> { "/api-v2/admin/groups/#{group.id}" },
    "GET /admin/groups/:id/users/" => -> { "/api-v2/admin/groups/#{group.id}/users/" },
    "GET /admin/users/" => -> { "/api-v2/admin/users/" },
    "GET /admin/users/:id" => -> { "/api-v2/admin/users/#{user.id}" },
    "GET /admin/people/" => -> { "/api-v2/admin/people/" }
  }.each do |description, path_builder|
    describe description do
      let(:path) { instance_exec(&path_builder) }
      include_examples :legacy_admin_only_access
    end
  end

  # Deliberate divergence: api-v2 adds the non-admin routes `/groups/` and
  # `/groups/:id` (e.g. to pick a group when granting permissions). They are
  # open to every authenticated user, anonymous requests are denied.
  {
    "GET /groups/" => -> { "/api-v2/groups/" },
    "GET /groups/:id" => -> { "/api-v2/groups/#{group.id}" }
  }.each do |description, path_builder|
    describe "#{description} (authenticated users only)" do
      let(:path) { instance_exec(&path_builder) }

      it "responds 401 to anonymous" do
        expect_legacy_status(anonymous_client.get(path), 401)
      end

      it "grants access to a user" do
        expect_legacy_status(client_for(user).get(path), ApiAccessParity::GRANTED)
      end

      it "grants access to an admin" do
        expect_legacy_status(client_for(create_admin_user).get(path), ApiAccessParity::GRANTED)
      end
    end
  end
end
