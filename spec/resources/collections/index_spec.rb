require "spec_helper"
require Pathname(File.expand_path("..", __FILE__)).join("shared")

describe "a bunch of collections with different properties" do
  include_context :bunch_of_collections

  describe "JSON `client` for public-`user`" do
    it "query responds with 200" do
      expect(plain_faraday_json_client.get("/api-v2/collections").status).to be == 200
    end
  end

  describe "JSON `client` for public-`user`" do
    include_context :json_client_for_public_user do
      describe "the collections resource" do
        let :resource do
          # collections # force evaluation
          client.get("/api-v2/collections")
        end

        it do
          expect(resource.status).to be == 200
        end
      end
    end
  end

  describe "JSON `client` for authenticated token-`admin`" do
    include_context :json_client_for_authenticated_token_admin do
      describe "the collections resource" do
        let :resource do
          # collections # force evaluation
          client.get("/api-v2/collections")
        end

        it do
          expect(resource.status).to be == 200
        end
      end
    end
  end

  describe "JSON `client` for public `user`" do
    include_context :json_client_for_public_user do
      describe "the collections resource" do
        let :resource do
          # collections # force evaluation
          client.get("/api-v2/collections")
        end

        it do
          expect(resource.status).to be == 200
        end
      end
    end
  end
end

describe "a bunch of collections with different properties" do
  include_context :bunch_of_collections

  describe "JSON `client` for public-`user`" do
    it "query responds with 403" do
      expect(plain_faraday_json_client.get("/api-v2/admin/collections").status).to be == 403
    end
  end

  describe "JSON `client` for public-`user`" do
    include_context :json_client_for_public_user do
      describe "the collections resource" do
        let :resource do
          # collections # force evaluation
          client.get("/api-v2/admin/collections")
        end

        it do
          expect(resource.status).to be == 403
        end
      end
    end
  end

  describe "JSON `client` for authenticated token-`user`" do
    include_context :json_client_for_authenticated_token_user do
      describe "the collections resource" do
        let :resource do
          # collections # force evaluation
          client.get("/api-v2/admin/collections")
        end

        it do
          expect(resource.status).to be == 403
        end
      end
    end
  end

  describe "JSON `client` for authenticated token-`admin`" do
    include_context :json_client_for_authenticated_token_admin do
      describe "the collections resource" do
        let :resource do
          # collections # force evaluation
          client.get("/api-v2/admin/collections")
        end

        it do
          expect(resource.status).to be == 200
        end
      end
    end
  end

  describe "JSON `client` for authenticated token-`admin` without creds" do
    include_context :json_client_for_authenticated_token_admin_no_creds do
      describe "the collections resource" do
        let :resource do
          # collections # force evaluation
          client.get("/api-v2/admin/collections")
        end

        it do
          expect(resource.status).to be == 403
        end
      end
    end
  end

  describe "JSON `client` for authenticated token-`user` without creds" do
    include_context :json_client_for_authenticated_token_user_no_creds do
      describe "the collections resource" do
        let :resource do
          # collections # force evaluation
          client.get("/api-v2/admin/collections")
        end

        it do
          expect(resource.status).to be == 403
        end
      end
    end
  end
end
