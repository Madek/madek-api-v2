require "spec_helper"

context "with a token without read and write scope" do
  include_context :json_client_for_authenticated_token_user_no_creds do
    let :response do
      client.get("/api-v2/auth-info/")
    end
    it "responds with 403" do
      expect(response.status).to be == 403
    end
  end
end

context "with a token with read and write scope" do
  include_context :json_client_for_authenticated_token_user do
    let :response do
      client.get("/api-v2/auth-info/")
    end

    it "responds with success 200" do
      expect(response.status).to be == 200
    end

    describe "the response body" do
      let :body do
        response.body
      end

      describe "the login property" do
        let :login do
          body["login"]
        end

        it "is the login of the token's user" do
          expect(login).to eq user.login
        end
      end

      describe "the authentication-method property" do
        let :authentication_method do
          body["authentication-method"]
        end
        it do
          expect(authentication_method).to be == "Token"
        end
      end

      describe "the type property" do
        let :type_property do
          body["type"]
        end
        it do
          expect(type_property).to eq "User"
        end
      end
    end
  end
end

describe "/auth-info resource" do
  context "without any authentication" do
    include_context :json_client_for_authenticated_token_user do
      context "via json" do
        let :response do
          plain_faraday_json_client.get("/api-v2/auth-info/")
        end

        it "responds with not authorized 401" do
          expect(response.status).to be == 401
        end
      end
    end
  end
end
