# Access variants for user routes. Admins are only privileged on
# /api-v2/admin/... routes, so on user routes a normal user and an admin need
# the same responsibility or permissions and must get the same answers.
module ResourceAccess
  # role => client context defining `user`, `token` and `client`
  ROLES = {
    user: :json_client_for_authenticated_token_user,
    admin: :json_client_for_authenticated_token_admin
  }.freeze

  GRANTING_VARIANTS = %i[responsible_user user_permission].freeze
  DENYING_VARIANT = :without_permission

  PERMISSION_FLAGS = {
    "Collection" => {
      factory: :collection_user_permission,
      flags: {get_metadata_and_previews: true, edit_metadata_and_relations: true, edit_permissions: true}
    },
    "MediaEntry" => {
      factory: :media_entry_user_permission,
      flags: {get_metadata_and_previews: true, get_full_size: true, edit_metadata: true, edit_permissions: true}
    }
  }.freeze

  module_function

  # :responsible_user   - the user is responsible for the resource
  # :user_permission    - someone else is responsible, the user has a full user permission
  # :without_permission - someone else is responsible, the user has no permission
  def setup(resource, user, variant)
    if variant == :responsible_user
      resource.update!(responsible_user: user, responsible_delegation: nil)
      return resource
    end

    if resource.responsible_user_id.nil? || resource.responsible_user_id == user.id
      resource.update!(responsible_user: FactoryBot.create(:user), responsible_delegation: nil)
    end

    if variant == :user_permission
      config = PERMISSION_FLAGS.fetch(resource.class.name)
      FactoryBot.create(config[:factory], **config[:flags],
        resource.model_name.singular.to_sym => resource, :user => user)
    end

    resource
  end
end
