require "spec_helper"
require Pathname(File.expand_path("../../", __FILE__)).join("shared")

describe "a bunch of media entries with different properties" do
  include_context :bunch_of_media_entries

  describe "JSON `client` for authenticated `user`" do
    include_context :json_client_for_authenticated_token_admin do
      describe "the media_entries resource" do
        before do
          create_5_media_entries # force evaluation
        end

        it "delete media_entries" do
          response = client.get("/api-v2/media-entries")
          expect(response.status).to be == 200
          expect(response.body["media_entries"].count).to eq(7)

          entries_to_delete = response.body["media_entries"][0..4]
          entries_to_delete = entries_to_delete.map { |entry| entry["id"] }
          entries_to_delete.each do |me_id|
            response = client.delete("/api-v2/media-entry/#{me_id}")
            expect(response.status).to be == 200
          end

          response = client.get("/api-v2/media-entries")
          expect(response.status).to be == 200

          entries_to_delete.each do |me_id|
            response = client.get("/api-v2/media-entry/#{me_id}/media-file")
            expect(response.status).to be == 404
            expect(response.body["message"]).to eq("No media-file for media_entry_id")
          end
        end

        it "delete media_entries" do
          response = client.get("/api-v2/media-entries")
          expect(response.status).to be == 200
          expect(response.body["media_entries"].count).to eq(7)

          me_id = response.body["media_entries"][0]["id"]

          response = client.get("/api-v2/media-entry/#{me_id}")
          expect(response.status).to be == 200
          expect(response.body["id"]).to eq(me_id)

          response = client.get("/api-v2/media-entry/#{me_id}/meta-data")
          expect(response.status).to be == 200
          expect(response.body["meta_data"][0]["media_entry_id"]).to eq(me_id)
          meta_key_id = response.body["meta_data"][0]["meta_key_id"]

          response = client.get("/api-v2/media-entry/#{me_id}/meta-datum/#{meta_key_id}")
          expect(response.status).to be == 200

          response = client.get("/api-v2/media-entry/#{me_id}/preview")
          expect(response.status).to be == 200
          expect(response.body["media_type"]).to eq("image")

          response = client.get("/api-v2/media-entry/#{me_id}/preview/data-stream")
          expect(response.status).to be == 200

          response = client.get("/api-v2/media-entry/#{me_id}/media-file")
          expect(response.status).to be == 200
          expect(response.body["media_type"]).to eq("image")

          media_entry_id = response.body["media_entry_id"]
          response = client.get("/api-v2/media-entry/#{me_id}/meta-data?media_entry_id=#{media_entry_id}")
          expect(response.status).to be == 200
          expect(response.body["meta_data"][0]["media_entry_id"]).to eq(media_entry_id)

          response = client.get("/api-v2/media-entry/#{me_id}/meta-datum/#{meta_key_id}")
          expect(response.status).to be == 200
          expect(response.body["meta_data"]["meta_key_id"]).to eq(meta_key_id)
        end

        it "checks conf-links" do
          response = client.get("/api-v2/media-entries")
          expect(response.status).to be == 200
          expect(response.body["media_entries"].count).to eq(7)

          me_id = response.body["media_entries"][0]["id"]
          response = client.get("/api-v2/media-entry/#{me_id}")
          expect(response.status).to be == 200
          expect(response.body["id"]).to eq(me_id)

          response = client.get("/api-v2/media-entry/#{me_id}/conf-links")
          expect(response.status).to be == 200
          expect(response.body[0]["resource_id"]).to eq(me_id)

          conf_link_id = response.body[0]["id"]

          response = client.get("/api-v2/media-entry/#{me_id}/conf-link/#{conf_link_id}")
          expect(response.status).to be == 200
          expect(response.body["id"]).to eq(conf_link_id)
        end

        it "checks media-file" do
          response = client.get("/api-v2/media-entries")
          expect(response.status).to be == 200
          expect(response.body["media_entries"].count).to eq(7)

          me_id = response.body["media_entries"][0]["id"]

          response = client.get("/api-v2/media-entry/#{me_id}")
          expect(response.status).to be == 200
          expect(response.body["id"]).to eq(me_id)

          response = client.get("/api-v2/media-entry/#{me_id}/media-file")
          expect(response.status).to be == 200
          expect(response.body["media_entry_id"]).to eq(me_id)

          response = client.get("/api-v2/media-entry/#{me_id}/media-file/data-stream")
          expect(response.status).to be == 200
        end

        it "checks meta-datum" do
          response = client.get("/api-v2/media-entries")
          expect(response.status).to be == 200
          expect(response.body["media_entries"].count).to eq(7)

          me_id = response.body["media_entries"][0]["id"]

          response = client.get("/api-v2/media-entry/#{me_id}")
          expect(response.status).to be == 200
          expect(response.body["id"]).to eq(me_id)

          response = client.get("/api-v2/media-entry/#{me_id}/meta-data")
          expect(response.status).to be == 200
          expect(response.body["meta_data"].count).to eq(6)

          ["test:people", "test:roles", "test:keywords"].each do |meta_key_id|
            response = client.get("/api-v2/media-entry/#{me_id}/meta-datum/#{meta_key_id}")
            expect(response.status).to be == 200
            expect(response.body["meta_data"]["media_entry_id"]).to eq(me_id)
          end

          response = client.get("/api-v2/media-entry/#{me_id}/meta-datum/test:keywords/keyword")
          expect(response.status).to be == 200

          response = client.get("/api-v2/media-entry/#{me_id}/meta-datum/test:people/people")
          expect(response.status).to be == 200

          response = client.get("/api-v2/media-entry/#{me_id}/meta-datum/test:roles/role")
          expect(response.status).to be == 200
        end
      end
    end
  end
end
