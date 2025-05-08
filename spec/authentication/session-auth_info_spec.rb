require "spec_helper"
require "cgi"
require "timecop"
require Pathname(File.expand_path("../..", __FILE__)).join("shared/clients")

# shared_context :valid_session_without_csrf do
#   let :user do
#     FactoryBot.create :user, password: "TOPSECRET"
#   end
#
#   let :user_session do
#     UserSession.create!(
#       user: user,
#       auth_system: AuthSystem.first.presence,
#       token_hash: "hashimotio",
#       created_at: Time.now
#     )
#   end
#
#   let :session_cookie do
#     CGI::Cookie.new("name" => Madek::Constants::MADEK_SESSION_COOKIE_NAME,
#       "value" => user_session.token)
#   end
#
#   let! :client do
#     session_auth_plain_faraday_json_client(session_cookie.to_s)
#   end
# end

describe "Session/Cookie Authentication" do
  include_context :valid_session_without_csrf

  context "expired session object" do
    it "the body indicates that the session has expired" do
      response = client.get("auth-info")
      expect(response.status).to eq 200
    end
  end
end
