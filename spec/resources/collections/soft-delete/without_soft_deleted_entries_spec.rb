require "spec_helper"
require Pathname(File.expand_path("../../", __FILE__)).join("shared")

describe "filtering collections" do
  include_context :full_setup_for_collections

  ResourceAccess::ROLES.each do |role, client_context|
    context "as #{role}" do
      ResourceAccess::GRANTING_VARIANTS.each do |access|
        context "with access via #{access}" do
          include_context client_context do
            before :each do
              user
              media_entries
              collection_4
              media_entry_with_media
              parent_collection

              (Collection.all.to_a + MediaEntry.all.to_a).each do |resource|
                ResourceAccess.setup(resource, user, access)
              end

              @deleted_child_ids = []

              puts "Found #{parent_collection.collections.count} child collections."
              parent_collection.collections.each do |child_collection|
                puts "Soft deleting collection ID: #{child_collection.id}"
                @deleted_child_ids << child_collection.id
              end
            end

            describe "/api-v2/collection-collection-arcs/" do
              it "fetch all existing" do
                response = client.get("/api-v2/collection-collection-arcs/")
                expect(response.status).to eq(200)
                expect(response.body["collection-collection-arcs"].count).to eq(4)
              end

              it "by parent and child" do
                response = client.get("/api-v2/collection-collection-arcs/?parent_id=#{parent_collection.id}&child_id=#{parent_collection.collections.first.id}")
                expect(response.status).to eq(200)
                expect(response.body["collection-collection-arcs"].count).to eq(1)
              end

              it "by parent" do
                response = client.get("/api-v2/collection-collection-arcs/?parent_id=#{parent_collection.id}")
                expect(response.status).to eq(200)
                expect(response.body["collection-collection-arcs"].count).to eq(4)
              end
            end

            describe "/api-v2/collection-media-entry-arcs/" do
              it "fetch all existing" do
                response = client.get("/api-v2/collection-media-entry-arcs/")
                expect(response.status).to eq(200)
                expect(response.body["collection-media-entry-arcs"].count).to eq(2)
              end

              it "by collection_id" do
                response = client.get("/api-v2/collection-media-entry-arcs/?collection_id=#{parent_collection.collections.first.id}")
                expect(response.status).to eq(200)
                expect(response.body["collection-media-entry-arcs"].count).to eq(1)

                mea = response.body["collection-media-entry-arcs"].first["id"]
                response = client.get("/api-v2/collection-media-entry-arcs/#{mea}")
                expect(response.status).to eq(200)
              end
            end

            describe "/api-v2/collections/{collection_id}" do
              it "fetch all existing" do
                response = client.get("/api-v2/collections/#{parent_collection.id}")
                expect(response.status).to eq(200)
              end
            end

            describe "/api-v2/collections/{collection_id}/media-entry-arcs" do
              it "fetch all existing" do
                response = client.get("/api-v2/collections/#{parent_collection.collections.first.id}/media-entry-arcs/")
                expect(response.status).to eq(200)
              end
            end

            describe "Modify /api-v2/collections/{collection_id}/media-entry-arcs" do
              it "fetch all existing" do
                response = client.get("/api-v2/collections/#{parent_collection.collections.first.id}/media-entry-arcs/")
                expect(response.status).to eq(200)
              end

              it "returns status 200 for GET request" do
                response = client.get("/api-v2/collections/#{parent_collection.collections.first.id}/media-entry-arcs/")
                expect(response.status).to eq(200)
              end

              it "creates, updates & deletes a new media entry arc" do
                media_entry_id = media_entries.first.id
                response = client.post("/api-v2/collections/#{parent_collection.collections.first.id}/media-entry-arcs/#{media_entry_id}") do |req|
                  req.body = {
                    "highlight" => true,
                    "cover" => true,
                    "position" => 0,
                    "order" => 0
                  }.to_json
                  req.headers["Content-Type"] = "application/json"
                end
                expect(response.status).to eq(200)
                expect(response.body["media_entry_id"]).to eq media_entry_id

                response = client.put("/api-v2/collections/#{parent_collection.collections.first.id}/media-entry-arcs/#{media_entry_id}") do |req|
                  req.body = {
                    "highlight" => false,
                    "cover" => false,
                    "position" => 1,
                    "order" => 1
                  }.to_json
                  req.headers["Content-Type"] = "application/json"
                end
                expect(response.status).to eq(200)
                expect(response.body["media_entry_id"]).to eq media_entry_id

                media_entry_id = media_entries.first.id
                response = client.delete("/api-v2/collections/#{parent_collection.collections.first.id}/media-entry-arcs/#{media_entry_id}")
                expect(response.status).to eq(200)

                response = client.delete("/api-v2/collections/#{parent_collection.collections.first.id}/media-entry-arcs/#{media_entry_id}")
                expect(response.status).to eq(404)
              end
            end

            describe "/api-v2/collections/{collection_id}/meta-data" do
              it "fetch all existing" do
                response = client.get("/api-v2/collections/#{parent_collection.collections.first.id}/meta-data/")
                expect(response.status).to eq(200)
                expect(response.body["meta_data"].count).to be > 0
              end
            end

            describe "/api-v2/collections/{collection_id}/media-entry-related" do
              it "fetch all existing" do
                response = client.get("/api-v2/collections/#{parent_collection.collections.first.id}/meta-data-related/")
                mk = response.body.first["meta_data"]["meta_key_id"]

                expect(response.status).to eq(200)
                expect(response.body.count).to eq(1)

                response = client.get("/api-v2/collections/#{parent_collection.collections.first.id}/meta-data-related/?meta_key_id=#{mk}")
                expect(response.status).to eq(200)
              end
            end

            describe "/api-v2/collections/{collection_id}/media-data/{meta_key_id}" do
              it "fetch all existing" do
                cid = parent_collection.collections.first.id
                response = client.get("/api-v2/collections/#{cid}/meta-data-related/")

                expect(response.status).to eq(200)
                expect(response.body.count).to eq(1)

                mk = response.body.first["meta_data"]["meta_key_id"]
                response = client.get("/api-v2/collections/#{cid}/meta-data/#{mk}")
                expect(response.status).to eq(200)
              end
            end

            describe "/api-v2/collections/{collection_id}/meta-data/{meta_key_id}/meta-data-people" do
              it "fetch all existing" do
                cid = parent_collection.collections.first.id
                response = client.get("/api-v2/collections/#{cid}/meta-data-related/")

                expect(response.status).to eq(200)
                expect(response.body.count).to eq(1)

                mk = response.body.first["meta_data"]["meta_key_id"]
                response = client.get("/api-v2/collections/#{cid}/meta-data/#{mk}/meta-data-people/")

                expect(response.status).to eq(200)
                expect(response.body["md_people"].count).to eq(1)
              end
            end

            describe "/api-v2/collections/{collection_id}/collection-arcs/{child_id}" do
              it "fetch all existing" do
                parent_id = parent_collection.id
                child_id = parent_collection.collections.first.id
                response = client.get("/api-v2/collections/#{parent_id}/collection-arcs/#{child_id}")
                expect(response.status).to eq(200)
                expect(response.body.count).to eq(8)
              end
            end

            describe "/api-v2/collections" do
              it "fetch all existing" do
                response = client.get("/api-v2/collections/")
                expect(response.status).to eq(200)
                expect(response.body["collections"].count).to be > 0

                response_ids = response.body["collections"].map { |collection| collection["id"] }
                response_ids.each do |id|
                  response = client.get("/api-v2/collections/#{id}")
                  expect(response.status).to eq(200)
                  expect(response.body["id"]).to eq(id)
                end
              end

              context "with soft-deleted entries" do
                before :each do
                  response = client.get("/api-v2/collections/")
                  expect(response.status).to be == 200
                  # pick a listed collection without existing keywords meta-datum (collection_3 has one)
                  @collection_id = response.body["collections"].map { |c| c["id"] }.find do |id|
                    !MetaDatum.exists?(collection_id: id, meta_key_id: "test:keywords")
                  end
                  collection = Collection.find(@collection_id)

                  meta_key_people = FactoryBot.create :meta_key_people
                  FactoryBot.create :meta_datum_people,
                    collection: collection,
                    meta_key: meta_key_people

                  @person = FactoryBot.create(:person)

                  roles_list = FactoryBot.create(:roles_list)
                  @meta_key_people_with_roles = FactoryBot.create(:meta_key_people_with_roles,
                    roles_list: roles_list)
                  @role = FactoryBot.create(:role)
                  roles_list.roles << @role

                  @mdp_wr = FactoryBot.create(:meta_datum_people_with_roles,
                    meta_key: @meta_key_people_with_roles,
                    collection: collection,
                    people_with_roles: [{person: @person, role: @role}])

                  meta_key_keywords = MetaKey.find_by(id: attributes_for(:meta_key_keywords)[:id]) \
                    || FactoryBot.create(:meta_key_keywords)

                  FactoryBot.create :meta_datum_keywords,
                    collection: collection,
                    meta_key: meta_key_keywords

                  meta_key_json = FactoryBot.create :meta_key_json
                  FactoryBot.create :meta_datum_json,
                    collection: collection,
                    meta_key: meta_key_json
                end

                it "checks meta-datum" do
                  response = client.get("/api-v2/collections/")
                  expect(response.status).to be == 200
                  collection_id = @collection_id
                  expect(response.body["collections"].map { |c| c["id"] }).to include(collection_id)

                  response = client.get("/api-v2/collections/#{collection_id}")
                  expect(response.status).to be == 200
                  expect(response.body["id"]).to eq(collection_id)

                  response = client.get("/api-v2/collections/#{collection_id}/meta-data/")
                  expect(response.status).to be == 200

                  ["test:people", "test:keywords"].each do |meta_key_id|
                    response = client.get("/api-v2/collections/#{collection_id}/meta-data/#{meta_key_id}")
                    expect(response.status).to be == 200
                    expect(response.body["meta_data"]["collection_id"]).to eq(collection_id)
                  end

                  # json
                  kw = "test:keywords2"
                  FactoryBot.create(:meta_key_json, id: kw)
                  response = client.post("/api-v2/collections/#{collection_id}/meta-data/#{kw}/json/") do |req|
                    req.body = {
                      json: "{}"
                    }.to_json
                    req.headers["Content-Type"] = "application/json"
                  end
                  expect(response.body).to be

                  response = client.get("/api-v2/collections/#{collection_id}/meta-data/#{kw}")
                  expect(response.status).to be == 200
                  expect(response.body).to be

                  response = client.get("/api-v2/collections/#{collection_id}/meta-data-related/")
                  expect(response.status).to be == 200
                  expect(response.body).to be

                  response = client.get("/api-v2/collections/#{collection_id}/meta-data/")
                  expect(response.status).to be == 200
                  expect(response.body).to be

                  response = client.put("/api-v2/collections/#{collection_id}/meta-data/#{kw}/json/") do |req|
                    req.body = {
                      json: {"foo" => "bar"}.to_json.to_s
                    }.to_json
                    req.headers["Content-Type"] = "application/json"
                  end
                  expect(response.status).to be == 200
                  expect(response.body).to be

                  response = client.delete("/api-v2/collections/#{collection_id}/meta-data/#{kw}")
                  expect(response.status).to be == 200
                  expect(response.body).to be

                  response = client.get("/api-v2/collections/#{collection_id}/meta-data/#{kw}")
                  expect(response.status).to be == 404

                  # keyword
                  response = client.get("/api-v2/collections/#{collection_id}/meta-data/test:keywords/keywords/")
                  expect(response.status).to be == 200

                  person_id = response.body["keywords_ids"].second

                  response = client.post("/api-v2/collections/#{collection_id}/meta-data/test:keywords/keywords/#{person_id}")
                  expect(response.status).to be == 406

                  response = client.delete("/api-v2/collections/#{collection_id}/meta-data/test:keywords/keywords/#{person_id}")
                  expect(response.status).to be == 200

                  response = client.post("/api-v2/collections/#{collection_id}/meta-data/test:keywords/keywords/#{person_id}")
                  expect(response.status).to be == 200

                  # people
                  response = client.get("/api-v2/collections/#{collection_id}/meta-data/test:people/meta-data-people/")
                  expect(response.status).to be == 200
                  expect(response.body["md_people"].count).to be 3

                  mdp = response.body["md_people"].second

                  response = client.delete("/api-v2/collections/#{collection_id}/meta-data/test:people/meta-data-people/#{mdp["id"]}")
                  expect(response.status).to be == 200

                  response = client.post("/api-v2/collections/#{collection_id}/meta-data/test:people/meta-data-people/") do |req|
                    req.body = {
                      "person_id" => mdp["person_id"]
                    }.to_json
                    req.headers["Content-Type"] = "application/json"
                  end
                  expect(response.status).to be == 200

                  # people with roles
                  meta_datum_person = MetaDatum::Person.find_by(meta_datum_id: @mdp_wr.id)
                  response = client.get("/api-v2/collections/#{collection_id}/meta-data/#{@meta_key_people_with_roles.id}/meta-data-people/#{meta_datum_person.id}/person")
                  expect(response.status).to be == 200
                  expect(response.body["id"]).to eq @person.id

                  response = client.get("/api-v2/collections/#{collection_id}/meta-data/#{@meta_key_people_with_roles.id}/meta-data-people/#{meta_datum_person.id}/role")
                  expect(response.status).to be == 200
                  expect(response.body["id"]).to eq @role.id
                end
              end

              it "fetch all existing conf-links" do
                response = client.get("/api-v2/collections/")
                expect(response.status).to eq(200)
                expect(response.body["collections"].count).to be > 0
                me_id = response.body["collections"].first["id"]

                response = client.get("/api-v2/collections/#{me_id}")
                expect(response.status).to be == 200
                expect(response.body["id"]).to eq(me_id)

                response = client.get("/api-v2/collections/#{me_id}/conf-links/")
                expect(response.status).to be == 200
                expect(response.body.count).to eq(0)

                response = client.post("/api-v2/collections/#{me_id}/conf-links/") do |req|
                  req.body = {
                    "revoked" => false,
                    "description" => "new conf link",
                    "expires_at" => (Time.zone.now + 10.day)
                  }.to_json
                  req.headers["Content-Type"] = "application/json"
                end
                expect(response.status).to be == 200
                cl_id = response.body["id"]

                response = client.get("/api-v2/collections/#{me_id}/conf-links/")
                expect(response.status).to be == 200
                expect(response.body.count).to eq(1)

                response = client.put("/api-v2/collections/#{me_id}/conf-links/#{cl_id}") do |req|
                  req.body = {
                    "revoked" => false,
                    "description" => "new conf link",
                    "expires_at" => (Time.zone.now + 5.day)
                  }.to_json
                  req.headers["Content-Type"] = "application/json"
                end
                expect(response.status).to be == 200

                conf_link_id = response.body["id"]
                response = client.get("/api-v2/collections/#{me_id}/conf-links/#{conf_link_id}")
                expect(response.status).to be == 200
                expect(response.body["id"]).to eq(conf_link_id)

                response = client.delete("/api-v2/collections/#{me_id}/conf-links/#{conf_link_id}")
                expect(response.status).to be == 200
                expect(response.body["id"]).to eq(conf_link_id)
              end
            end
          end
        end
      end

      context "without permissions" do
        include_context client_context

        before :each do
          media_entries
          media_entry_with_media
          parent_collection
          (Collection.all.to_a + MediaEntry.all.to_a).each do |resource|
            ResourceAccess.setup(resource, user, ResourceAccess::DENYING_VARIANT)
          end
        end

        let(:public_child) { parent_collection.collections.first }

        it "is forbidden to read the private parent collection and its arcs" do
          expect(client.get("/api-v2/collections/#{parent_collection.id}").status).to eq(403)
          expect(client.get("/api-v2/collections/#{parent_collection.id}/collection-arcs/#{public_child.id}").status).to eq(403)

          response = client.get("/api-v2/collection-collection-arcs/?parent_id=#{parent_collection.id}")
          expect(response.status).to eq(200)
          expect(response.body["collection-collection-arcs"]).to be_empty
        end

        it "lists and reads public children but not the private parent" do
          response = client.get("/api-v2/collections/")
          expect(response.status).to eq(200)
          ids = response.body["collections"].map { |c| c["id"] }
          expect(ids).not_to include(parent_collection.id)
          expect(ids).to include(public_child.id)

          expect(client.get("/api-v2/collections/#{public_child.id}").status).to eq(200)
          expect(client.get("/api-v2/collections/#{public_child.id}/meta-data/").status).to eq(200)
        end

        it "is forbidden to add a media-entry-arc to a public child" do
          media_entry_id = media_entries.first.id
          response = client.post("/api-v2/collections/#{public_child.id}/media-entry-arcs/#{media_entry_id}") do |req|
            req.body = {"highlight" => true, "cover" => true, "position" => 0, "order" => 0}.to_json
            req.headers["Content-Type"] = "application/json"
          end
          expect(response.status).to eq(403)
          expect(Arcs::CollectionMediaEntryArc.where(collection_id: public_child.id, media_entry_id: media_entry_id)).to be_empty
        end

        it "is forbidden to edit meta-data of a public child" do
          kw = "test:keywords2"
          FactoryBot.create(:meta_key_json, id: kw)
          response = client.post("/api-v2/collections/#{public_child.id}/meta-data/#{kw}/json/") do |req|
            req.body = {json: "{}"}.to_json
            req.headers["Content-Type"] = "application/json"
          end
          expect(response.status).to eq(403)
          expect(MetaDatum.where(collection_id: public_child.id, meta_key_id: kw)).to be_empty
        end

        it "is forbidden to manage conf-links of a public child" do
          expect(client.get("/api-v2/collections/#{public_child.id}/conf-links/").status).to eq(403)

          response = client.post("/api-v2/collections/#{public_child.id}/conf-links/") do |req|
            req.body = {"revoked" => false, "description" => "new conf link", "expires_at" => (Time.zone.now + 10.day)}.to_json
            req.headers["Content-Type"] = "application/json"
          end
          expect(response.status).to eq(403)
        end
      end
    end
  end
end
