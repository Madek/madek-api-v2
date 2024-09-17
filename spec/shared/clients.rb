shared_context :json_client_for_authenticated_entity do
  let :client do
    basic_auth_plain_faraday_json_client(entity.login, entity.password)
  end
end

shared_context :json_client_for_authenticated_user do |ctx|
  let :user do
    FactoryBot.create :user, password: "TOPSECRET"
  end

  let :entity do
    user
  end

  include_context :json_client_for_authenticated_entity

  describe "JSON `client` for authenticated `user`" do
    include_context ctx if ctx
  end
end

shared_context :json_client_for_authenticated_admin_user do |ctx|
  let :user do
    user = FactoryBot.create :user, password: "TOPSECRET"
    FactoryBot.create :admin, user: user
    user
  end

  let :entity do
    user
  end

  include_context :json_client_for_authenticated_entity

  describe "JSON `client` for authenticated `user`" do
    include_context ctx if ctx
  end
end

shared_context :json_client_for_authenticated_token_admin do |ctx|
  let :user do
    user = FactoryBot.create :user, password: "TOPSECRET"
    FactoryBot.create :admin, user: user
    user
  end

  let :token do
    ApiToken.create user: user, scope_read: true,
                    scope_write: true
  end

  let :entity do
    user
  end

  # include_context :json_client_for_authenticated_entity

  let :client do
    wtoken_header_plain_faraday_json_client(token.token)
  end

  describe "JSON `client` for authenticated `user`" do
    include_context ctx if ctx
  end
end

shared_context :json_client_for_authenticated_token_user do |ctx|
  let :user do
    FactoryBot.create :user, password: "TOPSECRET"
  end

  let :token do
    ApiToken.create user: user, scope_read: true,
                    scope_write: true
  end

  let :entity do
    user
  end

  # let :client_entity do
  #   user
  # end

  let :client do
    wtoken_header_plain_faraday_json_client(token.token)
  end
  # include_context :json_client_for_authenticated_entity

  describe "JSON `client` for authenticated `user`" do
    include_context ctx if ctx
  end
end

shared_context :json_client_for_authenticated_token_owner_user do |ctx|
  let :user do
    FactoryBot.create :user, password: "TOPSECRET"
  end

  let :user_token do
    ApiToken.create user: user, scope_read: true,
                    scope_write: true
  end

  let :user_token_no_creds do
    ApiToken.create user: user, scope_read: false,
                    scope_write: false
  end

  let :user_entity do
    user
  end

  let :user_client do
    wtoken_header_plain_faraday_json_client(user_token.token)
  end

  let :user_client_no_creds do
    wtoken_header_plain_faraday_json_client(user_token_no_creds.token)
  end

  let :owner do
    FactoryBot.create :user, password: "OWNER-TOPSECRET"
  end

  let :owner_token do
    ApiToken.create user: owner, scope_read: true,
                    scope_write: true
  end

  let :owner_entity do
    owner
  end

  let :owner_client do
    wtoken_header_plain_faraday_json_client(owner_token.token)
  end

  describe "JSON `client` for authenticated `user`" do
    include_context ctx if ctx
  end
end

shared_context :json_client_for_authenticated_token_admin_no_creds do |ctx|
  let :user do
    user = FactoryBot.create :user, password: "TOPSECRET"
    FactoryBot.create :admin, user: user
    user
  end

  let :token do
    ApiToken.create user: user, scope_read: false,
                    scope_write: false
  end

  let :entity do
    user
  end

  let :client do
    wtoken_header_plain_faraday_json_client(token.token)
  end
  # include_context :json_client_for_authenticated_entity

  describe "JSON `client` for authenticated `user`" do
    include_context ctx if ctx
  end
end

shared_context :json_client_for_authenticated_token_user_no_creds do |ctx|
  let :user do
    FactoryBot.create :user, password: "TOPSECRET"
  end

  let :token do
    ApiToken.create user: user, scope_read: false,
                    scope_write: false
  end

  let :entity do
    user
  end

  let :client do
    wtoken_header_plain_faraday_json_client(token.token)
  end
  # include_context :json_client_for_authenticated_entity

  describe "JSON `client` for authenticated `user`" do
    include_context ctx if ctx
  end
end

shared_context :json_client_for_authenticated_api_client do |ctx|
  let :api_client do
    FactoryBot.create :api_client, password: "TOPSECRET"
  end

  let :entity do
    api_client
  end

  include_context :json_client_for_authenticated_entity

  describe "JSON `client` for authenticated `api_client`" do
    include_context ctx if ctx
  end
end

shared_context :authenticated_json_client do |_ctx|
  # if rand < 0.5

  # TODO token auth
  # TODO session-auth

  let :user do
    FactoryBot.create :user, password: "TOPSECRET"
  end
  let :api_client do
    nil
  end
  # else
  #  let :user do
  #    nil
  #  end
  #  let :api_client do
  #    FactoryBot.create :api_client, password: 'TOPSECRET'
  #  end
  # end

  let :client_entity do
    user # || api_client
  end

  let :authenticated_json_client do
    basic_auth_plain_faraday_json_client(client_entity.login, client_entity.password)
  end
end
