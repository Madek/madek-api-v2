require 'spec_helper'
require 'madek_open_session'
require 'cgi'
require 'timecop'

shared_examples :responds_with_success do
  it 'responds with success 200' do
    expect(response.status).to be == 200
  end
end

shared_examples :responds_with_not_authorized do
  it 'responds with 401 not authorized' do
    expect(response.status).to be == 401
  end
end

describe 'API-Token Authentication' do
  let :user do
    FactoryBot.create :user, password: 'TOPSECRET'
  end

  let :response do
    client.get('auth-info')
  end

  context 'revoking the token ' do

    context 'initially unrevoked token ' do
      let :token do
        ApiToken.create user: user, scope_read: true,
          scope_write: true
      end

      context 'used in basic auth' do
        let :client do
          basic_auth_plain_faraday_json_client(token.token, nil)
        end
        it 'accessing auth-info results in 200' do
          expect(response.status).to be == 200
        end
      end

      context 'after revoking token ' do

        before :each do
          token.update! revoked: true
        end

        context 'used in basic auth' do
          let :client do
            basic_auth_plain_faraday_json_client(token.token, nil)
          end
          it 'accessing auth-info results in 401' do
            expect(response.status).to be == 401
          end
        end

      end

    end

  end

  context 'prolonging an expired token ' do
    let :token do
      ApiToken.create user: user, scope_read: true,
        scope_write: true, expires_at: (Time.zone.now - 1.day)
    end

    context 'used in basic auth' do
      let :client do
        basic_auth_plain_faraday_json_client(token.token, nil)
      end
      it 'accessing auth-info results in 401' do
        expect(response.status).to be == 401
      end
    end

    context 'after prolonging the token' do
      before :each do
        token.update! expires_at: (Time.zone.now + 1.day)
      end
      context 'used in basic auth' do
        let :client do
          basic_auth_plain_faraday_json_client(token.token, nil)
        end
        it 'accessing auth-info results in 401' do
          expect(response.status).to be == 200
        end
      end
    end

  end

  context 'read only token connection' do
    let :token do
      ApiToken.create user: user, scope_read: true, scope_write: false
    end

    context 'connection via token as basic auth user' do
      let :client do
        basic_auth_plain_faraday_json_client(token.token, nil)
      end
      it 'enables to read the auth-info' do
        expect(response.status).to be == 200
      end
    end

    context 'connection via token as password and some "nonsense" as username' do
      let :client do
        basic_auth_plain_faraday_json_client("nonsense", token.token)
      end
      it 'enables to read the auth-info' do
        expect(response.status).to be == 200
      end
    end

    context 'connection via token "Authorization: token TOKEN" header' do
      let :client do
        basic_auth_wtoken_header_plain_faraday_json_client(token.token, nil, token.token)
      end

      it 'enables to read the auth-info' do
        expect(response.status).to be == 200
      end

      it 'is forbidden to use an unsafe http verb' do
        delete_response = client.delete('auth-info') #.data[:href])
        expect(delete_response.status).to be == 403
      end
    end
  end

  context 'write only token connection' do
    let :token do
      ApiToken.create user: user, scope_read: false, scope_write: true
    end
    let :client do
      basic_auth_plain_faraday_json_client(token.token, nil)
    end
    it 'reading auth_info results in forbidden ' do
      expect(response.status).to be == 403
    end
  end
end
