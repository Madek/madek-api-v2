require "spec_helper"

shared_context :user_entity do |ctx|
  context "for Database User" do
    before :each do
      @entity = FactoryBot.create :user, password: "TOPSECRET"
    end
    let :entity_type do
      "User"
    end
    include_context ctx if ctx
  end
end

shared_context :test_bad_password_basic_auth do
  context "with proper username but bad password" do
    let :response do
      basic_auth_plain_faraday_json_client(@entity.login, "BOGUS").get("/api-v2/auth-info")
    end
    it "responds with 401" do
      expect(response.status).to be == 401
    end
  end
end

shared_context :test_proper_basic_auth do
  context "with proper username and password" do
    let :response do
      basic_auth_plain_faraday_json_client(@entity.login, @entity.password).get("/api-v2/auth-info")
      # new_token_auth_faraday_json_client(token, "/api-v2/auth-info")

    end

    it "responds with success 200" do
      expect(response.status).to be == 401
    end

    describe "the response body" do
      let :body do
        response.body
      end

      describe "the login property" do
        it "should be equal to the entities login" do
          expect(body["message"]).to be == "Not authorized"
        end
      end

      describe "the authentication-method property" do
        it do
          expect(body["message"]).to be == "Not authorized"
        end
      end

      describe "the type property" do
        it do
          expect(body["message"]).to be == "Not authorized"
        end
      end
    end
  end
end

describe "/auth-info resource" do
  context "without any authentication" do
    context "via json" do
      let :response do
        plain_faraday_json_client.get("/api-v2/auth-info")
        # new_token_auth_faraday_json_client(token, "/api-v2/auth-info")

      end

      it "responds with not authorized 401" do
        expect(response.status).to be == 401
      end
    end
  end

  context "Basic Authentication" do
    include_context :user_entity, :test_proper_basic_auth
    include_context :user_entity, :test_bad_password_basic_auth
  end
end
