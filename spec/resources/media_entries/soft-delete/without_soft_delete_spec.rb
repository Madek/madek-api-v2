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
          response = client.get("/api-v2/media-entries/")
          expect(response.status).to be == 200
          expect(response.body["media_entries"].count).to eq(7)

          entries_to_delete = response.body["media_entries"][0..4]
          entries_to_delete = entries_to_delete.map { |entry| entry["id"] }
          entries_to_delete.each do |me_id|
            response = client.delete("/api-v2/media-entries/#{me_id}")
            expect(response.status).to be == 200
          end

          response = client.get("/api-v2/media-entries/")
          expect(response.status).to be == 200

          entries_to_delete.each do |me_id|
            response = client.get("/api-v2/media-entries/#{me_id}/media-files/")
            expect(response.status).to be == 404
            expect(response.body["message"]).to eq("No media-file for media_entry_id")
          end
        end

        it "delete media_entries" do
          response = client.get("/api-v2/media-entries/")
          expect(response.status).to be == 200
          expect(response.body["media_entries"].count).to eq(7)

          me_id = response.body["media_entries"][0]["id"]

          response = client.get("/api-v2/media-entries/#{me_id}")
          expect(response.status).to be == 200
          expect(response.body["id"]).to eq(me_id)

          response = client.get("/api-v2/media-entries/#{me_id}/meta-data/")
          expect(response.status).to be == 200
          expect(response.body["meta_data"][0]["media_entry_id"]).to eq(me_id)
          meta_key_id = response.body["meta_data"][0]["meta_key_id"]

          response = client.get("/api-v2/media-entries/#{me_id}/meta-data/#{meta_key_id}")
          expect(response.status).to be == 200

          response = client.get("/api-v2/media-entries/#{me_id}/previews/")
          expect(response.status).to be == 200
          expect(response.body["media_type"]).to eq("image")

          response = client.get("/api-v2/media-entries/#{me_id}/previews/data-stream/")
          expect(response.status).to be == 200

          response = client.get("/api-v2/media-entries/#{me_id}/media-files/")
          expect(response.status).to be == 200
          expect(response.body["media_type"]).to eq("image")

          media_entry_id = response.body["media_entry_id"]
          response = client.get("/api-v2/media-entries/#{me_id}/meta-data/?media_entry_id=#{media_entry_id}")
          expect(response.status).to be == 200
          expect(response.body["meta_data"][0]["media_entry_id"]).to eq(media_entry_id)

          response = client.get("/api-v2/media-entries/#{me_id}/meta-data/#{meta_key_id}")
          expect(response.status).to be == 200
          expect(response.body["meta_data"]["meta_key_id"]).to eq(meta_key_id)
        end

        it "checks conf-links" do
          response = client.get("/api-v2/media-entries/")
          expect(response.status).to be == 200
          expect(response.body["media_entries"].count).to eq(7)

          me_id = response.body["media_entries"][0]["id"]
          response = client.get("/api-v2/media-entries/#{me_id}")
          expect(response.status).to be == 200
          expect(response.body["id"]).to eq(me_id)

          response = client.get("/api-v2/media-entries/#{me_id}/conf-links/")
          expect(response.status).to be == 200
          expect(response.body[0]["resource_id"]).to eq(me_id)

          response = client.post("/api-v2/media-entries/#{me_id}/conf-links/") do |req|
            req.body = {
              "revoked" => false,
              "description" => "new conf link",
              "expires_at" => (Time.zone.now + 10.day)
            }.to_json
            req.headers["Content-Type"] = "application/json"
          end
          expect(response.status).to be == 200
          cl_id = response.body["id"]

          response = client.put("/api-v2/media-entries/#{me_id}/conf-links/#{cl_id}") do |req|
            req.body = {
              "revoked" => false,
              "description" => "new conf link",
              "expires_at" => (Time.zone.now + 5.day)
            }.to_json
            req.headers["Content-Type"] = "application/json"
          end
          expect(response.status).to be == 200

          conf_link_id = response.body["id"]
          response = client.get("/api-v2/media-entries/#{me_id}/conf-links/#{conf_link_id}")
          expect(response.status).to be == 200
          expect(response.body["id"]).to eq(conf_link_id)

          response = client.delete("/api-v2/media-entries/#{me_id}/conf-links/#{conf_link_id}")
          expect(response.status).to be == 200
          expect(response.body["id"]).to eq(conf_link_id)
        end

        it "checks media-file" do
          response = client.get("/api-v2/media-entries/")
          expect(response.status).to be == 200
          expect(response.body["media_entries"].count).to eq(7)

          me_id = response.body["media_entries"][0]["id"]

          response = client.get("/api-v2/media-entries/#{me_id}")
          expect(response.status).to be == 200
          expect(response.body["id"]).to eq(me_id)

          response = client.get("/api-v2/media-entries/#{me_id}/media-files/")
          expect(response.status).to be == 200
          expect(response.body["media_entry_id"]).to eq(me_id)

          response = client.get("/api-v2/media-entries/#{me_id}/media-files/data-stream/")
          expect(response.status).to be == 200
        end

        it "checks meta-datum" do
          response = client.get("/api-v2/media-entries/")
          expect(response.status).to be == 200
          expect(response.body["media_entries"].count).to eq(7)

          me_id = response.body["media_entries"][0]["id"]

          response = client.get("/api-v2/media-entries/#{me_id}")
          expect(response.status).to be == 200
          expect(response.body["id"]).to eq(me_id)

          response = client.get("/api-v2/media-entries/#{me_id}/meta-data/")
          expect(response.status).to be == 200
          expect(response.body["meta_data"].count).to eq(6)

          ["test:people", "test:roles", "test:keywords"].each do |meta_key_id|
            response = client.get("/api-v2/media-entries/#{me_id}/meta-data/#{meta_key_id}")
            expect(response.status).to be == 200
            expect(response.body["meta_data"]["media_entry_id"]).to eq(me_id)
          end

          # json
          kw = "test:keywords2"
          FactoryBot.create(:meta_key_json, id: kw)
          response = client.post("/api-v2/media-entries/#{me_id}/meta-data/#{kw}/json/") do |req|
            req.body = {
              json: "{}"
            }.to_json
            req.headers["Content-Type"] = "application/json"
          end
          expect(response.body).to be

          response = client.get("/api-v2/media-entries/#{me_id}/meta-data/#{kw}")
          expect(response.status).to be == 200
          expect(response.body).to be

          response = client.get("/api-v2/media-entries/#{me_id}/meta-data-related/")
          expect(response.status).to be == 200
          expect(response.body).to be

          response = client.get("/api-v2/media-entries/#{me_id}/meta-data/")
          expect(response.status).to be == 200
          expect(response.body).to be

          response = client.put("/api-v2/media-entries/#{me_id}/meta-data/#{kw}/json/") do |req|
            req.body = {
              json: {"foo" => "bar"}.to_json.to_s
            }.to_json
            req.headers["Content-Type"] = "application/json"
          end
          expect(response.status).to be == 200
          expect(response.body).to be

          response = client.delete("/api-v2/media-entries/#{me_id}/meta-data/#{kw}")
          expect(response.status).to be == 200
          expect(response.body).to be

          response = client.get("/api-v2/media-entries/#{me_id}/meta-data/#{kw}")
          expect(response.status).to be == 404

          # keyword
          response = client.get("/api-v2/media-entries/#{me_id}/meta-data/test:keywords/keywords/")
          expect(response.status).to be == 200

          person_id = response.body["keywords_ids"].second

          response = client.post("/api-v2/media-entries/#{me_id}/meta-data/test:keywords/keywords/#{person_id}")
          expect(response.status).to be == 406

          response = client.delete("/api-v2/media-entries/#{me_id}/meta-data/test:keywords/keywords/#{person_id}")
          expect(response.status).to be == 200

          response = client.post("/api-v2/media-entries/#{me_id}/meta-data/test:keywords/keywords/#{person_id}")
          expect(response.status).to be == 200

          # people
          response = client.get("/api-v2/media-entries/#{me_id}/meta-data/test:people/people/")
          expect(response.status).to be == 200
          expect(response.body["md_people"].count).to be 3

          person_id = response.body["people_ids"].second

          response = client.post("/api-v2/media-entries/#{me_id}/meta-data/test:people/people/#{person_id}")
          expect(response.status).to be == 406

          response = client.delete("/api-v2/media-entries/#{me_id}/meta-data/test:people/people/#{person_id}")
          expect(response.status).to be == 200

          response = client.post("/api-v2/media-entries/#{me_id}/meta-data/test:people/people/#{person_id}")
          expect(response.status).to be == 200

          # role
          response = client.get("/api-v2/media-entries/#{me_id}/meta-data/test:roles/roles/")
          expect(response.status).to be == 200

          role_id = response.body["roles"].first["id"]
          person_id = response.body["md_roles"].second["person_id"]
          response = client.post("/api-v2/media-entries/#{me_id}/meta-data/test:roles/roles/#{role_id}/#{person_id}/11")
          expect(response.status).to be == 200

          role_id = response.body["md_roles"].first["role_id"]
          person_id = response.body["md_roles"].first["person_id"]
          response = client.delete("/api-v2/media-entries/#{me_id}/meta-data/test:roles/roles/#{role_id}/#{person_id}")
          expect(response.status).to be == 200
        end
      end
    end
  end
end
