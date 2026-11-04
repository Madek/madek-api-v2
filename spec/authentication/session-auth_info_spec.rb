require "spec_helper"
require "cgi"
require Pathname(File.expand_path("../..", __FILE__)).join("shared/clients")

describe "Session/Cookie Authentication" do
  include_context :valid_session_without_csrf

  context "valid session object" do
    it "auth-info responds with 200" do
      response = client.get("auth-info/")
      expect(response.status).to eq 200
    end
  end
end
