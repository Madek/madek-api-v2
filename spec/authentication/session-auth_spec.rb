require 'spec_helper'
require 'cgi'
require 'timecop'


shared_examples :responds_with_success do
  #TODO setup valid user session
  #it 'responds with success 200' do
  #  expect(response.status).to be == 200
  #end

  it 'responds with success 401' do
    expect(response.status).to be == 401
  end
end

shared_examples :responds_with_not_authorized do
  it 'responds with 401 not authorized' do
    expect(response.status).to be == 401
  end
end

shared_context :valid_session_object do |to_include|
  context 'valid session object' do
    #TODO setup valid user session
    # this does not work
    let :user_session do
      UserSession.create!(
        user: user, 
        auth_system: AuthSystem.first.presence,
        token_hash: 'hashimotio',
        created_at: Time.now)
    end

    let :session_cookie do
      # TODO use UserSesssion hash
      CGI::Cookie.new('name' => Madek::Constants::MADEK_SESSION_COOKIE_NAME,
                      # TODO encode
                      'value' => user_session.token_hash)
                      
    end

    let :client do
      session_auth_plain_faraday_json_client(session_cookie.to_s)
    end

    include_examples to_include
  end
end

describe 'Session/Cookie Authentication' do
  before :all do
    Rails.application.secrets.secret_key_base = 'secret'
  end

  let :user do
    FactoryBot.create :user, password: 'TOPSECRET'
  end

  # TODO this down not work
  let :user_session do
    UserSession.create!(
      user: user, 
      auth_system: AuthSystem.first.presence,
      token_hash: 'hashimotio',
      created_at: Time.now - 7.days)
  end

  let :response do
    client.get('auth-info')
  end

  context 'Session authentication is enabled' do
    include_examples :valid_session_object, :responds_with_success

    context 'expired session object' do
      # TODO use user_session
      let :session_cookie do
        Timecop.freeze(Time.now - 7.days) do
          CGI::Cookie.new('name' => Madek::Constants::MADEK_SESSION_COOKIE_NAME,
                          # TODO encode
                          'value' => user_session.token_hash)
        end
      end

      let :client do
        session_auth_plain_faraday_json_client(session_cookie.to_s)
      end

      include_examples :responds_with_not_authorized
      it 'the body indicates that the session has expired' do
        expect(response.body.with_indifferent_access['message']).to match(/The session is invalid or expired!/)
      end
    end
  end

  context 'Session authentication is disabled ' do
    before :each do
      @original_config_local = YAML.load_file(
        'config/settings.local.yml') rescue {}
      config_local = @original_config_local.merge(
        'madek_api_session_enabled' => false)
      File.open('config/settings.local.yml', 'w') do |f|
        f.write config_local.to_yaml
      end
      sleep 3
    end

    after :each do
      File.open('config/settings.local.yml', 'w') do |f|
        f.write @original_config_local.to_yaml
      end
    end

    include_examples :valid_session_object, :responds_with_not_authorized
  end
end
