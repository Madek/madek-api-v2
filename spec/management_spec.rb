require 'spec_helper'

describe '/management/status' do
    let :url do
        "/api/management/status"
    end
    context 'without authentication' do
        let :response do
            plain_faraday_json_client.get(url)
        end
        it 'responds with not authorized 401' do
            expect(response.status).to be == 401
        end
    end

    context 'with wrong authentication' do
        let :response do
            basic_auth_plain_faraday_json_client("bogus","bogus").get(url)
        end
        it 'responds with not authorized 401' do
            expect(response.status).to be == 401
        end
    end

    context 'with correct authentication' do
        let :response do
            basic_auth_plain_faraday_json_client("u1login","secret").get(url)
        end
        it 'responds with 200' do
            expect(response.status).to be == 200
        end
    end 
end



describe '/management/shutdown' do
    let :url do
        "/api/management/shutdown"
    end

    context 'without authentication' do
        let :response do
            plain_faraday_json_client.post(url)
        end
        it 'responds with not authorized 401' do
            expect(response.status).to be == 401
        end
    end

    context 'with wrong authentication' do
        let :response do
            basic_auth_plain_faraday_json_client("bogus","bogus").post(url)
        end
        it 'responds with not authorized 401' do
            expect(response.status).to be == 401
        end
    end

    context 'with incorrect method' do
        let :response do
            basic_auth_plain_faraday_json_client("","secret").get(url)
        end
        it 'responds with 405' do
            expect(response.status).to be == 405
        end
    end 

    # do not include shutdown test if not running it manually
    #context 'with correct authentication' do
    #    let :response do
    #        basic_auth_plain_faraday_json_client("u1login","secret").post(url)
    #    end
    #    it 'responds with 200' do
    #        expect(response.status).to be == 204
    #    end
    #end 
end
