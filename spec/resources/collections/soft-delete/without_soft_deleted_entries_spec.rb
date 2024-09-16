require "spec_helper"
require Pathname(File.expand_path("../../", __FILE__)).join("shared")

describe "filtering collections" do
  include_context :full_setup_for_collections

  include_context :json_client_for_authenticated_token_admin do
    before :each do
      user
      media_entries
      collection_4
      media_entry_with_media
      parent_collection

      @deleted_child_ids = []

      puts "Found #{parent_collection.collections.count} child collections."
      parent_collection.collections.each do |child_collection|
        puts "Soft deleting collection ID: #{child_collection.id}"
        @deleted_child_ids << child_collection.id
      end
    end

    describe "/api-v2/collection-collection-arcs/" do
      it "fetch all existing" do
        response = client.get("/api-v2/collection-collection-arcs")
        expect(response.status).to eq(200)
        expect(response.body["collection-collection-arcs"].count).to eq(4)
      end

      it "by parent and child" do
        response = client.get("/api-v2/collection-collection-arcs?parent_id=#{parent_collection.id}&child_id=#{parent_collection.collections.first.id}")
        expect(response.status).to eq(200)
        expect(response.body["collection-collection-arcs"].count).to eq(1)
      end

      it "by parent" do
        response = client.get("/api-v2/collection-collection-arcs?parent_id=#{parent_collection.id}")
        expect(response.status).to eq(200)
        expect(response.body["collection-collection-arcs"].count).to eq(4)
      end
    end

    describe "/api-v2/collection-media-entry-arcs/" do
      it "fetch all existing" do
        response = client.get("/api-v2/collection-media-entry-arcs")
        expect(response.status).to eq(200)
        expect(response.body["collection-media-entry-arcs"].count).to eq(2)
      end

      it "by collection_id" do
        response = client.get("/api-v2/collection-media-entry-arcs?collection_id=#{parent_collection.collections.first.id}")
        expect(response.status).to eq(200)
        expect(response.body["collection-media-entry-arcs"].count).to eq(1)

        mea = response.body["collection-media-entry-arcs"].first["id"]
        response = client.get("/api-v2/collection-media-entry-arcs/#{mea}")
        expect(response.status).to eq(200)
      end
    end

    describe "/api-v2/collection/{collection_id}" do
      it "fetch all existing" do
        response = client.get("/api-v2/collection/#{parent_collection.id}")
        expect(response.status).to eq(200)
      end
    end

    describe "/api-v2/collection/{collection_id}/media-entry-arcs" do
      it "fetch all existing" do
        response = client.get("/api-v2/collection/#{parent_collection.collections.first.id}/media-entry-arcs")
        expect(response.status).to eq(200)
      end
    end

    describe "/api-v2/collection/{collection_id}/meta-data" do
      it "fetch all existing" do
        response = client.get("/api-v2/collection/#{parent_collection.collections.first.id}/meta-data")
        expect(response.status).to eq(200)
        expect(response.body["meta-data"].count).to be > 0
      end
    end

    describe "/api-v2/collection/{collection_id}/media-entry-related" do
      it "fetch all existing" do
        response = client.get("/api-v2/collection/#{parent_collection.collections.first.id}/meta-data-related")
        mk = response.body.first["meta-data"]["meta_key_id"]

        expect(response.status).to eq(200)
        expect(response.body.count).to eq(1)

        response = client.get("/api-v2/collection/#{parent_collection.collections.first.id}/meta-data-related?meta_key_id=#{mk}")
        expect(response.status).to eq(200)
      end
    end

    describe "/api-v2/collection/{collection_id}/media-data/{meta_key_id}" do
      it "fetch all existing" do
        cid = parent_collection.collections.first.id
        response = client.get("/api-v2/collection/#{cid}/meta-data-related")

        expect(response.status).to eq(200)
        expect(response.body.count).to eq(1)

        mk = response.body.first["meta-data"]["meta_key_id"]
        response = client.get("/api-v2/collection/#{cid}/meta-datum/#{mk}")
        expect(response.status).to eq(200)
      end
    end

    describe "/api-v2/collection/{collection_id}/media-data/{meta_key_id}/people" do
      it "fetch all existing" do
        cid = parent_collection.collections.first.id
        response = client.get("/api-v2/collection/#{cid}/meta-data-related")

        expect(response.status).to eq(200)
        expect(response.body.count).to eq(1)

        mk = response.body.first["meta-data"]["meta_key_id"]
        response = client.get("/api-v2/collection/#{cid}/meta-datum/#{mk}/people")

        expect(response.status).to eq(200)
        expect(response.body["people_ids"].count).to eq(1)
      end
    end

    describe "/api-v2/collection/{collection_id}/collection-arc/{child_id}" do
      it "fetch all existing" do
        parent_id = parent_collection.id
        child_id = parent_collection.collections.first.id
        response = client.get("/api-v2/collection/#{parent_id}/collection-arc/#{child_id}")
        expect(response.status).to eq(200)
        expect(response.body.count).to eq(8)
      end
    end

    describe "/api-v2/collections" do
      it "fetch all existing" do
        response = client.get("/api-v2/collections")
        expect(response.status).to eq(200)
        expect(response.body["collections"].count).to be > 0

        response_ids = response.body["collections"].map { |collection| collection["id"] }
        response_ids.each do |id|
          response = client.get("/api-v2/collection/#{id}")
          expect(response.status).to eq(200)
          expect(response.body["id"]).to eq(id)
        end
      end
    end
  end
end
