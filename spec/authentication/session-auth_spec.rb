require "spec_helper"
require "cgi"
require "timecop"
require Pathname(File.expand_path("../..", __FILE__)).join("shared/clients")

shared_examples :responds_with_not_authorized do
  it "responds with Unauthorized 401" do
    expect(response.status).to be == 401
  end
end

shared_examples :responds_with_authorized do
  it "responds with Authorized 200" do
    expect(response.status).to be == 200
  end
end

describe "Session/Cookie Authentication" do
  let :user do
    FactoryBot.create :user, password: "TOPSECRET"
  end

  let :user_session do
    UserSession.create!(
      user: user,
      auth_system: AuthSystem.first.presence,
      token_hash: "hashimotio",
      created_at: Time.now - 7.days
    )
  end

  let :response do
    client.get("auth-info")
  end

  context "Session authentication is enabled" do
    include_examples :valid_session_without_csrf, :responds_with_authorized

    context "expired session object" do
      let :session_cookie do
        Timecop.freeze(Time.now - 7.days) do
          CGI::Cookie.new("name" => Madek::Constants::MADEK_SESSION_COOKIE_NAME,
            "value" => user_session.token)
        end
      end

      let :client do
        puts "session cookie: #{session_cookie}"
        session_auth_plain_faraday_json_client(session_cookie.to_s)
      end

      it "the body indicates that the session has expired" do
        expect(response.status).to eq 401
        expect(response.body.with_indifferent_access["message"]).to match(/The session is invalid or expired!/)
      end
    end
  end

  context "Session authentication is enabled" do
    include_examples :valid_session_without_csrf, :responds_with_authorized

    context "expired session object" do
      let :user_session do
        UserSession.create!(
          user: user,
          auth_system: AuthSystem.first.presence,
          token_hash: "hashimotio",
          created_at: Time.now
        )
      end

      let :session_cookie do
        Timecop.freeze(Time.now) do
          CGI::Cookie.new("name" => Madek::Constants::MADEK_SESSION_COOKIE_NAME,
            "value" => user_session.token)
        end
      end

      let :client do
        puts "session cookie: #{session_cookie}"
        session_auth_plain_faraday_json_client(session_cookie.to_s)
      end

      it "the body indicates that the session has expired" do
        expect(response.status).to eq 200
      end
    end
  end

  context "Session authentication is disabled " do
    before :each do
      @original_config_local = begin
        YAML.load_file(
          "config/settings.local.yml"
        )
      rescue
        {}
      end
      config_local = @original_config_local.merge(
        "madek_api_session_enabled" => false
      )
      File.write("config/settings.local.yml", config_local.to_yaml)
      sleep 3
    end

    after :each do
      File.write("config/settings.local.yml", @original_config_local.to_yaml)
    end

    include_examples :valid_session_without_csrf, :responds_with_not_authorized
  end
end
