shared_context :json_client_for_authenticated_entity do
  let :client do
    wtoken_header_plain_faraday_json_client(token.token)
  end
end

shared_context :valid_session_base do |as_admin = false|
  let(:user) do
    user = FactoryBot.create(:user, password: "TOPSECRET")
    FactoryBot.create :admin, user: user if as_admin
    user
  end

  let(:user_session) do
    UserSession.create!(
      user: user,
      auth_system: AuthSystem.first.presence,
      token_hash: "hashimotio",
      created_at: Time.now
    )
  end
end

shared_context :valid_session_without_csrf do
  include_context :valid_session_base

  let(:session_cookie) do
    CGI::Cookie.new(
      "name" => Madek::Constants::MADEK_SESSION_COOKIE_NAME,
      "value" => user_session.token
    ).to_s
  end

  let!(:client) do
    session_auth_plain_faraday_json_client(session_cookie)
  end
end

shared_context :valid_session_with_csrf do
  include_context :valid_session_base

  let(:csrf_token) { "ab63c8ab-a224-441d-9251-8068f6245252" }

  let(:session_cookie) do
    session_name = Madek::Constants::MADEK_SESSION_COOKIE_NAME
    csrf_name = "madek-anti-csrf-token"

    "#{session_name}=#{user_session.token}; #{csrf_name}=#{csrf_token}"
  end

  let!(:client) do
    session_auth_plain_faraday_json_client(
      session_cookie,
      {"x-csrf-token" => csrf_token}
    )
  end
end

shared_context :valid_admin_session_with_csrf do
  include_context :valid_session_base, true

  let(:csrf_token) { "ab63c8ab-a224-441d-9251-8068f6245252" }

  let(:session_cookie) do
    session_name = Madek::Constants::MADEK_SESSION_COOKIE_NAME
    csrf_name = "madek-anti-csrf-token"

    "#{session_name}=#{user_session.token}; #{csrf_name}=#{csrf_token}"
  end

  let!(:client) do
    session_auth_plain_faraday_json_client(
      session_cookie,
      {"x-csrf-token" => csrf_token}
    )
  end
end

shared_context :json_client_for_public_user do |ctx|
  let :user do
    FactoryBot.create :user, password: "TOPSECRET"
  end

  let :entity do
    user
  end

  let :client do
    plain_faraday_json_client
  end

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

shared_context :json_client_for_authenticated_token_base do |ctx, user_as_admin, user_read, user_write|
  let :user do
    user = FactoryBot.create :user, password: "TOPSECRET"
    if user_as_admin
      FactoryBot.create :admin, user: user
    end
    user
  end

  let :token do
    ApiToken.create user: user, scope_read: user_read, scope_write: user_write
  end

  let :entity do
    user
  end

  let :client do
    wtoken_header_plain_faraday_json_client(token.token)
  end

  describe "JSON `client` for authenticated `user`" do
    include_context ctx if ctx
  end
end

shared_context :json_client_for_authenticated_token_owner_user do |ctx|
  let :user do
    FactoryBot.create :user, password: "TOPSECRET"
  end

  let :token do
    ApiToken.create user: user, scope_read: true, scope_write: true
  end

  let :user_token_no_creds do
    ApiToken.create user: user, scope_read: false, scope_write: false
  end

  let :user_entity do
    user
  end

  let :user_client do
    wtoken_header_plain_faraday_json_client(token.token)
  end

  let :user_client_no_creds do
    wtoken_header_plain_faraday_json_client(user_token_no_creds.token)
  end

  let :owner do
    FactoryBot.create :user, password: "OWNER-TOPSECRET"
  end

  let :owner_token do
    ApiToken.create user: owner, scope_read: true, scope_write: true
  end

  describe "JSON `client` for authenticated `user`" do
    include_context ctx if ctx
  end
end

shared_context :json_client_for_authenticated_token_admin do |ctx|
  include_context :json_client_for_authenticated_token_base, ctx, true, true, true
end

shared_context :json_client_for_authenticated_token_admin_no_creds do |ctx|
  include_context :json_client_for_authenticated_token_base, ctx, true, false, false
end

shared_context :json_client_for_authenticated_token_user do |ctx|
  include_context :json_client_for_authenticated_token_base, ctx, false, true, true
end

shared_context :json_client_for_authenticated_token_user_no_creds do |ctx|
  include_context :json_client_for_authenticated_token_base, ctx, false, false, false
end

shared_context :json_client_for_authenticated_token_user_read do |ctx|
  include_context :json_client_for_authenticated_token_base, ctx, false, true, false
end

shared_context :json_client_for_authenticated_token_user_write do |ctx|
  include_context :json_client_for_authenticated_token_base, ctx, false, false, true
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
    plain_faraday_json_client
  end
end
