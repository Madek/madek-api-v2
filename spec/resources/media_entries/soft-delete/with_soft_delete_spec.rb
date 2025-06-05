require "spec_helper"
require Pathname(File.expand_path("../../", __FILE__)).join("shared")

describe "a bunch of media entries with different properties" do
  include_context :bunch_of_media_entries

  describe "JSON `client` for authenticated `user`" do
    include_context :json_client_for_authenticated_token_admin do
      describe "the soft-deleted media_entries resource" do
        before do
          create_5_media_entries # force evaluation
          create_5_media_entries.each do |me|
            me.soft_delete
          end
        end

        it "delete media_entries" do
          response = client.get("/api-v2/media-entries/")
          expect(response.status).to be == 200
          expect(response.body["media_entries"].count).to eq(0)

          entries_to_delete = response.body["media_entries"][0..4]
          entries_to_delete = entries_to_delete.map { |entry| entry["id"] }
          entries_to_delete.each do |me_id|
            response = client.delete("/api-v2/media-entries/#{me_id}")
            expect(response.status).to be == 200
          end

          response = client.get("/api-v2/media-entries/")
          expect(response.status).to be == 200

          entries_to_delete.each do |me_id|
            response = client.get("/api-v2/media-entries/#{me_id}/media-files")
            expect(response.status).to be == 404
            expect(response.body["message"]).to eq("No media-file for media_entry_id")
          end
        end

        it "checks preview" do
          response = client.get("/api-v2/media-entries/")
          expect(response.status).to be == 200
          expect(response.body["media_entries"].count).to eq(0)

          soft_deleted_me_id = MediaEntry.unscoped.all.first.id

          response = client.get("/api-v2/media-entries/#{soft_deleted_me_id}")
          expect(response.status).to be == 404

          response = client.get("/api-v2/media-entries/#{soft_deleted_me_id}/meta-data/")
          expect(response.status).to be == 404

          response = client.get("/api-v2/media-entries/#{soft_deleted_me_id}/previews/")
          expect(response.status).to be == 404

          response = client.get("/api-v2/media-entries/#{soft_deleted_me_id}/preview/data-stream/")
          expect(response.status).to be == 404

          response = client.get("/api-v2/media-entries/#{soft_deleted_me_id}/media-files/")
          expect(response.status).to be == 404
        end

        it "checks conf-links" do
          response = client.get("/api-v2/media-entries/")
          expect(response.status).to be == 200
          expect(response.body["media_entries"].count).to eq(0)

          soft_deleted_me_id = MediaEntry.unscoped.all.first.id

          response = client.get("/api-v2/media-entries/#{soft_deleted_me_id}")
          expect(response.status).to be == 404

          response = client.get("/api-v2/media-entries/#{soft_deleted_me_id}/conf-links/")
          expect(response.status).to be == 404
        end

        it "checks media-file" do
          response = client.get("/api-v2/media-entries/")
          expect(response.status).to be == 200
          expect(response.body["media_entries"].count).to eq(0)

          soft_deleted_me_id = MediaEntry.unscoped.all.first.id

          response = client.get("/api-v2/media-entries/#{soft_deleted_me_id}")
          expect(response.status).to be == 404

          response = client.get("/api-v2/media-entries/#{soft_deleted_me_id}/media-files/")
          expect(response.status).to be == 404

          response = client.get("/api-v2/media-entries/#{soft_deleted_me_id}/media-files/data-stream/")
          expect(response.status).to be == 404
        end

        it "checks media-entries" do
          response = client.get("/api-v2/media-entries/")
          expect(response.status).to be == 200
          expect(response.body["media_entries"].count).to eq(0)

          soft_deleted_me_id = MediaEntry.unscoped.all.first.id

          response = client.get("/api-v2/media-entries/#{soft_deleted_me_id}")
          expect(response.status).to be == 404

          response = client.get("/api-v2/media-entries/#{soft_deleted_me_id}/meta-data/")
          expect(response.status).to be == 404

          ["test:people", "test:roles", "test:keywords"].each do |meta_key_id|
            response = client.get("/api-v2/media-entries/#{soft_deleted_me_id}/meta-datum/#{meta_key_id}")
            expect(response.status).to be == 404
          end

          response = client.get("/api-v2/media-entries/#{soft_deleted_me_id}/meta-datum/test:keywords/keyword")
          expect(response.status).to be == 404

          response = client.get("/api-v2/media-entries/#{soft_deleted_me_id}/meta-datum/test:people/people")
          expect(response.status).to be == 404

          response = client.get("/api-v2/media-entries/#{soft_deleted_me_id}/meta-datum/test:roles/role")
          expect(response.status).to be == 404
        end
      end
    end
  end
end
