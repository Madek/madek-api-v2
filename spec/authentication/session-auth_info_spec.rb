require "spec_helper"
require "cgi"
require "timecop"
require Pathname(File.expand_path("../..", __FILE__)).join("shared/clients")

describe "Session/Cookie Authentication" do
  include_context :valid_session_without_csrf

  context "expired session object" do
    it "the body indicates that the session has expired" do
      response = client.get("auth-info")
      expect(response.status).to eq 200
    end
  end
end
