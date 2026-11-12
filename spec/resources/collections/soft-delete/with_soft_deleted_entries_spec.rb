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

                child_collection.soft_delete
              end
            end

            describe "/api-v2/collections/*/collection-arcs/" do
              it "fetches, creates, updates, and deletes a collection-arc" do
                child_id = parent_collection.collections.first.id
                response = client.get("/api-v2/collections/#{parent_collection.id}/collection-arcs/#{child_id}")
                expect(response.status).to eq(200)

                new_child = ResourceAccess.setup(FactoryBot.create(:collection), user, access)
                create_data = {"highlight" => true, "order" => 0, "position" => 0}
                response = client.post("/api-v2/collections/#{parent_collection.id}/collection-arcs/#{new_child.id}") do |req|
                  req.body = create_data.to_json
                  req.headers["Content-Type"] = "application/json"
                end
                expect(response.status).to eq(200)

                update_data = {"highlight" => false, "order" => 2, "position" => 1}
                response = client.put("/api-v2/collections/#{parent_collection.id}/collection-arcs/#{new_child.id}") do |req|
                  req.body = update_data.to_json
                  req.headers["Content-Type"] = "application/json"
                end
                expect(response.status).to eq(200)

                response = client.delete("/api-v2/collections/#{parent_collection.id}/collection-arcs/#{new_child.id}")
                expect(response.status).to eq(200)
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
                expect(response.status).to eq(404)
              end
            end

            describe "/api-v2/collections/{collection_id}/meta-data" do
              it "fetch all existing" do
                response = client.get("/api-v2/collections/#{parent_collection.collections.first.id}/meta-data/")
                expect(response.status).to eq(404)
              end
            end

            describe "/api-v2/collections/{collection_id}/media-entry-related" do
              it "fetch all existing" do
                response = client.get("/api-v2/collections/#{parent_collection.collections.first.id}/meta-data-related/")
                expect(response.status).to eq(404)
              end
            end

            describe "/api-v2/collections/{collection_id}/media-data/{meta_key_id}" do
              it "fetch all existing" do
                cid = parent_collection.collections.first.id
                response = client.get("/api-v2/collections/#{cid}/meta-data-related/")

                expect(response.status).to eq(404)
              end
            end

            describe "/api-v2/collections/{collection_id}/media-data/{meta_key_id}/people/" do
              it "fetch all existing" do
                cid = parent_collection.collections.first.id
                response = client.get("/api-v2/collections/#{cid}/meta-data-related/")
                expect(response.status).to eq(404)
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
                  expect(@deleted_child_ids).not_to include(id)
                end

                response_ids.each do |id|
                  response = client.get("/api-v2/collections/#{id}")
                  expect(response.status).to eq(200)
                  expect(response.body["id"]).to eq(id)
                end
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

          @deleted_child_ids = parent_collection.collections.map(&:id)
          parent_collection.collections.each(&:soft_delete)
        end

        it "is forbidden to read the private parent collection and its arcs" do
          child_id = @deleted_child_ids.first

          expect(client.get("/api-v2/collections/#{parent_collection.id}").status).to eq(403)
          expect(client.get("/api-v2/collections/#{parent_collection.id}/collection-arcs/#{child_id}").status).to eq(403)

          response = client.get("/api-v2/collection-collection-arcs/?parent_id=#{parent_collection.id}")
          expect(response.status).to eq(200)
          expect(response.body["collection-collection-arcs"]).to be_empty
        end

        it "is forbidden to create a collection-arc on the private parent collection" do
          new_child = ResourceAccess.setup(FactoryBot.create(:collection), user, ResourceAccess::DENYING_VARIANT)
          response = client.post("/api-v2/collections/#{parent_collection.id}/collection-arcs/#{new_child.id}") do |req|
            req.body = {"highlight" => true, "order" => 0, "position" => 0}.to_json
            req.headers["Content-Type"] = "application/json"
          end
          expect(response.status).to eq(403)
          expect(Arcs::CollectionCollectionArc.where(parent_id: parent_collection.id, child_id: new_child.id)).to be_empty
        end

        it "does not list the private parent collection nor soft-deleted children" do
          response = client.get("/api-v2/collections/")
          expect(response.status).to eq(200)
          ids = response.body["collections"].map { |c| c["id"] }
          expect(ids).not_to include(parent_collection.id)
          expect(ids & @deleted_child_ids).to be_empty
        end
      end
    end
  end
end
