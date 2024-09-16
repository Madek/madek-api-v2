require "spec_helper"
require Pathname(File.expand_path("..", __FILE__)).join("shared")

describe "a bunch of media entries with different properties" do
  include_context :bunch_of_media_entries

  include_context :json_client_for_authenticated_token_user do
    describe "JSON `client` for authenticated `user`" do
      describe "the media_entries resource" do
        let :resource do
          media_entries # force evaluation
          client.get("/api-v2/media-entries")
        end

        it do
          expect(resource.status).to be == 200
        end
      end
    end

    describe "the media_entries resource without read/write creds" do
      include_context :json_client_for_authenticated_token_user_no_creds do
        let :resource do
          media_entries # force evaluation
          client.get("/api-v2/media-entries")
        end

        it do
          expect(resource.status).to be == 403
        end
      end
    end
  end
end
