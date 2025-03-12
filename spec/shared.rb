
shared_examples :responds_with_success do
  # TODO setup valid user session
  # it 'responds with success 200' do
  #  expect(response.status).to be == 200
  # end

  it "responds with success 401" do
    expect(response.status).to be == 401
  end
end

shared_examples :responds_with_not_authorized do
  it "responds with 401 not authorized" do
    expect(response.status).to be == 401
  end
end

shared_context :valid_session_object do |to_include|
  context "valid session object" do
    # TODO setup valid user session
    # this does not work
    let :user_session do
      UserSession.create!(
        user: user,
        auth_system: AuthSystem.first.presence,
        token_hash: "hashimotio",
        created_at: Time.now
      )
    end

    let :session_cookie do
      # TODO use UserSesssion hash
      CGI::Cookie.new("name" => Madek::Constants::MADEK_SESSION_COOKIE_NAME,
                      # TODO encode
                      "value" => user_session.token_hash)
    end

    let :client do
      session_auth_plain_faraday_json_client(session_cookie.to_s)
    end

    include_examples to_include
  end
end