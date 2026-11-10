require "spec_helper"
require Pathname(File.expand_path("../../", __FILE__)).join("shared")

# The 7 entries of `create_5_media_entries` are public (get_metadata_and_previews)
# and belong to random users. Writes, conf-links and full-size downloads need
# responsibility or permissions, for admins as for every other user.
describe "a bunch of media entries with different properties" do
  include_context :bunch_of_media_entries

  ResourceAccess::ROLES.each do |role, client_context|
    describe "JSON `client` for authenticated #{role}" do
      include_context client_context do
        ResourceAccess::GRANTING_VARIANTS.each do |access|
          describe "the media_entries resource with access via #{access}" do
            before do
              create_5_media_entries.each do |me|
                ResourceAccess.setup(me, user, access)
              end
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
              expect(response.body).to be_a Array
              expect(response.body.first.keys).to include("id", "thumbnail")

              preview_id = response.body.first["id"]
              response = client.get("/api-v2/previews/#{preview_id}/data-stream")
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
              expect(response.body["meta_data"].count).to eq(5)

              ["test:people", "test:keywords"].each do |meta_key_id|
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
              response = client.get("/api-v2/media-entries/#{me_id}/meta-data/test:people/meta-data-people/")
              expect(response.status).to be == 200
              expect(response.body["md_people"].count).to be 3

              mdp = response.body["md_people"].second

              response = client.delete("/api-v2/media-entries/#{me_id}/meta-data/test:people/meta-data-people/#{mdp["id"]}")
              expect(response.status).to be == 200

              response = client.post("/api-v2/media-entries/#{me_id}/meta-data/test:people/meta-data-people/") do |req|
                req.body = {
                  "person_id" => mdp["person_id"]
                }.to_json
                req.headers["Content-Type"] = "application/json"
              end
              expect(response.status).to be == 200
            end
          end
        end

        describe "the media_entries resource without permissions" do
          let(:me_id) { create_5_media_entries.first.id }

          before do
            create_5_media_entries.each do |me|
              ResourceAccess.setup(me, user, ResourceAccess::DENYING_VARIANT)
            end
          end

          def json_request(method, url, payload = nil)
            client.public_send(method, url) do |req|
              req.body = payload.to_json if payload
              req.headers["Content-Type"] = "application/json"
            end
          end

          it "lists and reads the public media_entries" do
            response = client.get("/api-v2/media-entries/")
            expect(response.status).to be == 200
            expect(response.body["media_entries"].map { |me| me["id"] }).to match_array(create_5_media_entries.map(&:id))

            expect(client.get("/api-v2/media-entries/#{me_id}").status).to be == 200
            expect(client.get("/api-v2/media-entries/#{me_id}/meta-data/").status).to be == 200
            expect(client.get("/api-v2/media-entries/#{me_id}/media-files/").status).to be == 200
          end

          it "is forbidden to delete media_entries" do
            expect(client.delete("/api-v2/media-entries/#{me_id}").status).to be == 403
            expect(MediaEntry.find_by(id: me_id)).to be
          end

          it "is forbidden to manage conf-links" do
            expect(client.get("/api-v2/media-entries/#{me_id}/conf-links/").status).to be == 403

            response = json_request(:post, "/api-v2/media-entries/#{me_id}/conf-links/",
              {"revoked" => false, "description" => "new conf link", "expires_at" => (Time.zone.now + 10.day)})
            expect(response.status).to be == 403
            expect(ConfidentialLink.where(resource_id: me_id).count).to eq(1)
          end

          it "is forbidden to download the media-file without get_full_size" do
            MediaEntry.find(me_id).update_columns(get_full_size: false)
            expect(client.get("/api-v2/media-entries/#{me_id}/media-files/data-stream/").status).to be == 403
          end

          it "is forbidden to edit meta-data" do
            kw = "test:keywords2"
            FactoryBot.create(:meta_key_json, id: kw)
            expect(json_request(:post, "/api-v2/media-entries/#{me_id}/meta-data/#{kw}/json/", {json: "{}"}).status).to be == 403
            expect(MetaDatum.where(media_entry_id: me_id, meta_key_id: kw)).to be_empty

            response = client.get("/api-v2/media-entries/#{me_id}/meta-data/test:keywords/keywords/")
            expect(response.status).to be == 200
            keyword_id = response.body["keywords_ids"].first
            expect(client.delete("/api-v2/media-entries/#{me_id}/meta-data/test:keywords/keywords/#{keyword_id}").status).to be == 403

            response = client.get("/api-v2/media-entries/#{me_id}/meta-data/test:people/meta-data-people/")
            expect(response.status).to be == 200
            mdp = response.body["md_people"].first
            expect(client.delete("/api-v2/media-entries/#{me_id}/meta-data/test:people/meta-data-people/#{mdp["id"]}").status).to be == 403
            expect(client.get("/api-v2/media-entries/#{me_id}/meta-data/test:people/meta-data-people/").body["md_people"].count).to eq(3)
          end
        end
      end
    end
  end
end
