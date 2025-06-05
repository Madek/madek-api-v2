require "spec_helper"
require "cgi"
require "timecop"
require "uri"
require Pathname(File.expand_path("../..", __FILE__)).join("shared/clients")

describe "Session/Cookie Authentication" do
  let :user do
    FactoryBot.create :user, password: "TOPSECRET"
  end

  context "Session authentication as admin" do
    before :each do
      FactoryBot.create(:custom_url, creator: user)
      Workflow.create(name: Faker::Educator.course, creator: user)
    end

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
        session_auth_plain_faraday_json_client(session_cookie.to_s)
      end

      it "returns expected keys when fetching /custom_urls (schema/coercion)" do
        default_keys = ["id", "media_entry_id", "collection_id"]
        paths = [
          ["/api-v2/custom_urls/", 200, default_keys],
          ["/api-v2/custom_urls/?fields=", 200, default_keys],
          ["/api-v2/custom_urls/?fields=creator_id", 200, ["creator_id"]]
        ]

        paths.each do |path, expected_status, existing_keys|
          response = client.get(path)

          expect(response.status).to eq(expected_status)
          expect(response.body.first.keys).to eq(existing_keys)
        end
      end

      it "returns expected keys when fetching /groups (clojure/spec)" do
        default_keys = ["institution", "institutional_id", "name", "type", "searchable", "updated_at", "id",
          "created_by_user_id", "institutional_name", "created_at"]
        paths = [
          ["/api-v2/groups/", 200, default_keys],
          ["/api-v2/groups/?fields=", 200, default_keys],
          ["/api-v2/groups/?fields=created_at", 200, ["created_at"]]
        ]

        paths.each do |path, expected_status, existing_keys|
          response = client.get(path)

          expect(response.status).to eq(expected_status)
          expect(response.body["groups"].first.keys).to eq(existing_keys)
        end
      end
    end
  end
end
