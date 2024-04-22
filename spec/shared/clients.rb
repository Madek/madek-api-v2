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
