require "spec_helper"
require "cgi"
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
  let :response do
    client.get("auth-info/")
  end

  context "Session authentication is enabled" do
    context "valid session" do
      include_context :valid_session_without_csrf
      include_examples :responds_with_authorized
    end

    context "expired session" do
      include_context :valid_session_without_csrf

      let :user_session do
        UserSession.create!(
          user: user,
          auth_system: AuthSystem.first.presence,
          token_hash: "hashimotio",
          created_at: Time.now - 7.days
        )
      end

      include_examples :responds_with_not_authorized

      it "the body indicates that the session has expired" do
        expect(response.body.with_indifferent_access["message"]).to match(/The session is invalid or expired!/)
      end
    end
  end

  context "Session authentication is disabled" do
    let(:config_local_path) { "config/settings.local.yml" }

    before :each do
      @original_config_local = File.exist?(config_local_path) ? File.read(config_local_path) : nil
      config_local = (@original_config_local && YAML.safe_load(@original_config_local)) || {}
      File.write(config_local_path, config_local.merge("madek_api_session_enabled" => false).to_yaml)
      sleep 3
    end

    after :each do
      # the server merges config files into its current config, so the
      # setting has to be re-enabled explicitly before the file is removed
      config_local = (@original_config_local && YAML.safe_load(@original_config_local)) || {}
      File.write(config_local_path, {"madek_api_session_enabled" => true}.merge(config_local).to_yaml)
      sleep 3
      if @original_config_local
        File.write(config_local_path, @original_config_local)
      else
        File.delete(config_local_path)
      end
    end

    include_context :valid_session_without_csrf
    include_examples :responds_with_not_authorized
  end
end
