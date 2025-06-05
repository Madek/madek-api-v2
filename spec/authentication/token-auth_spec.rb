require "spec_helper"
require "cgi"
require "timecop"

shared_examples :responds_with_unauthorized do
  it "responds with success 200" do
    expect(response.status).to be == 200
  end
end

shared_examples :responds_with_not_authorized do
  it "responds with 401 not authorized" do
    expect(response.status).to be == 401
  end
end

describe "API-Token Authentication" do
  let :response do
    client.get("/api-v2/auth-info/")
  end

  context "revoking the token " do
    context "initially unrevoked token " do
      let :user do
        FactoryBot.create :user, password: "TOPSECRET"
      end
      let :token do
        ApiToken.create user: user, scope_read: true,
          scope_write: true
      end

      context "after revoking token " do
        before :each do
          token.update! revoked: true
        end

        context "used in token auth" do
          let :client do
            wtoken_header_plain_faraday_json_client(token.token)
          end
          it "accessing auth-info results in 401" do
            expect(response.status).to be == 401
          end
        end
      end
    end

    context "initially unrevoked token " do
      include_context :json_client_for_authenticated_token_user do
        context "used in token auth" do
          it "accessing auth-info results in 200" do
            expect(response.status).to be == 200
          end
        end

        context "after revoking token " do
          before :each do
            token.update! revoked: true
          end

          context "used in token auth" do
            it "accessing auth-info results in 401" do
              expect(response.status).to be == 401
            end
          end
        end
      end
    end

    context "prolonging an expired token " do
      include_context :json_client_for_authenticated_token_user do
        let :token do
          ApiToken.create user: user, scope_read: true,
            scope_write: true, expires_at: (Time.zone.now - 1.day)
        end

        context "used in token auth" do
          let :client do
            wtoken_header_plain_faraday_json_client(token.token)
          end
          it "accessing auth-info results in 401" do
            expect(response.status).to be == 401
          end
        end

        context "after prolonging the token" do
          before :each do
            token.update! expires_at: (Time.zone.now + 1.day)
          end
          context "used in token auth" do
            let :client do
              wtoken_header_plain_faraday_json_client(token.token)
            end
            it "accessing auth-info results in 200" do
              expect(response.status).to be == 200
            end
          end
        end
      end
    end

    context "read only token connection" do
      include_context :json_client_for_authenticated_token_user_read do
        context "connection via token as token-user" do
          it "enables to read the auth-info/" do
            expect(response.status).to be == 200
          end
        end

        context 'connection via token as password and some "nonsense" as username' do
          it "enables to read the auth-info/" do
            expect(response.status).to be == 200
          end
        end

        context 'connection via token "Authorization: token TOKEN" header' do
          it "enables to read the auth-info/" do
            expect(response.status).to be == 200
          end

          it "is forbidden to use an unsafe http verb" do
            delete_response = client.delete("auth-info/") # .data[:href])
            expect(delete_response.status).to be == 405
          end
        end
      end
    end

    context "write only token connection" do
      include_context :json_client_for_authenticated_token_user_write do
        it "reading auth_info results in forbidden " do
          expect(response.status).to be == 403
        end
      end
    end
  end

  describe "Access to public /api-endpoints by API-Token" do
    include_context :json_client_for_authenticated_token_user do
      let :response do
        client.get("/api-v2/vocabularies/?page=1&count=100")
      end

      context "using an authorized token " do
        context "used in token auth" do
          it "accessing auth-info results in 200" do
            expect(response.status).to be == 200
          end
        end

        context "used in token auth" do
          it "accessing auth-info results in 200" do
            expect(response.status).to be == 200
          end
        end

        context "used in token auth" do
          it "accessing auth-info results in 200" do
            expect(response.status).to be == 200
          end
        end

        context "used in token auth" do
          it "accessing auth-info results in 200" do
            expect(response.status).to be == 200
          end
        end
      end
    end
  end

  describe "Access forbidden for /admin-endpoints by API-Token" do
    include_context :json_client_for_authenticated_token_user do
      let :response do
        client.get("/api-v2/admin/vocabularies/?page=1&count=100")
      end

      context "using an authorized token " do
        context "used in token auth" do
          it "access forbidden auth-info results in 403" do
            expect(response.status).to be == 403
          end
        end

        context "used in token auth" do
          it "access forbidden auth-info results in 403" do
            expect(response.status).to be == 403
          end
        end

        context "used in token auth" do
          it "access forbidden auth-info results in 403" do
            expect(response.status).to be == 403
          end
        end

        context "used in token auth" do
          it "access forbidden auth-info results in 403" do
            expect(response.status).to be == 403
          end
        end
      end
    end
  end
end
