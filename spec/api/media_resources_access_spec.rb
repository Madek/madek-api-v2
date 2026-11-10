require "spec_helper"
require Pathname(File.expand_path("..", __FILE__)).join("shared")

# Legacy relations: media-entry, collection, meta-datum, meta-datum-person,
# meta-datum-data-stream, media-file, preview.

describe "Access parity with the legacy API: media-resources" do
  let(:owner) { FactoryBot.create :user }

  describe "media-entry" do
    let(:media_resource) { FactoryBot.create :media_entry, responsible_user: owner }

    describe "GET /media-entries/:id" do
      let(:path) { "/api-v2/media-entries/#{media_resource.id}" }
      include_examples :legacy_media_resource_access
    end

    describe "GET /media-entries/:id/meta-data/" do
      let(:path) { "/api-v2/media-entries/#{media_resource.id}/meta-data/" }
      include_examples :legacy_media_resource_access
    end

    context "with a meta-datum" do
      let(:meta_datum) { FactoryBot.create :meta_datum_text, media_entry: media_resource }

      describe "GET /meta-data/:id" do
        let(:path) { "/api-v2/meta-data/#{meta_datum.id}" }
        include_examples :legacy_media_resource_access
      end

      describe "GET /meta-data/:id/data-stream" do
        let(:path) { "/api-v2/meta-data/#{meta_datum.id}/data-stream/" }
        include_examples :legacy_media_resource_access
      end

      # legacy: /media-entries/:id/meta-data/:meta_key_id/data-stream redirects
      # to the meta-datum, which is authorized as above
      describe "GET /media-entries/:id/meta-data/:meta_key_id" do
        let(:path) { "/api-v2/media-entries/#{media_resource.id}/meta-data/#{meta_datum.meta_key_id}" }
        include_examples :legacy_media_resource_access
      end
    end

    # The legacy `/meta-data-people/:id` is not authorized at all (leak); api-v2
    # has to apply the rules of the parent media-entry instead.
    # `.../meta-data-people/:id` itself answers 500 for an existing id (handler
    # bug), so the access decision is checked on its `/person` sub-resource.
    context "with a meta-datum-person" do
      let(:meta_datum) { FactoryBot.create :meta_datum_people, media_entry: media_resource }
      let(:meta_datum_person) { meta_datum.meta_data_people.first }

      describe "GET /media-entries/:id/meta-data/:meta_key_id/meta-data-people/:id/person" do
        let(:path) do
          "/api-v2/media-entries/#{media_resource.id}/meta-data/#{meta_datum.meta_key_id}" \
            "/meta-data-people/#{meta_datum_person.id}/person"
        end
        include_examples :legacy_media_resource_access
      end
    end

    context "with a media-file and a preview" do
      let(:media_file) { FactoryBot.create :media_file_for_image, media_entry: media_resource }
      let(:preview) { FactoryBot.create :preview, media_file: media_file, content_type: "image/jpeg" }

      describe "GET /media-files/:id" do
        let(:path) { "/api-v2/media-files/#{media_file.id}" }
        include_examples :legacy_media_resource_access
      end

      describe "GET /media-files/:id/data-stream" do
        let(:path) { "/api-v2/media-files/#{media_file.id}/data-stream/" }
        include_examples :legacy_media_resource_access, :get_full_size
      end

      # legacy: /media-entries/:id/media-file/data-stream redirects to the
      # media-file data-stream, which is authorized as above
      describe "GET /media-entries/:id/media-files/data-stream" do
        let(:path) do
          media_file
          "/api-v2/media-entries/#{media_resource.id}/media-files/data-stream/"
        end
        include_examples :legacy_media_resource_access, :get_full_size
      end

      describe "GET /previews/:id" do
        let(:path) { "/api-v2/previews/#{preview.id}" }
        include_examples :legacy_media_resource_access
      end
    end
  end

  describe "collection" do
    let(:media_resource) { FactoryBot.create :collection, responsible_user: owner }

    describe "GET /collections/:id" do
      let(:path) { "/api-v2/collections/#{media_resource.id}" }
      include_examples :legacy_media_resource_access
    end

    describe "GET /collections/:id/meta-data/" do
      let(:path) { "/api-v2/collections/#{media_resource.id}/meta-data/" }
      include_examples :legacy_media_resource_access
    end

    context "with a meta-datum" do
      let(:meta_datum) { FactoryBot.create :meta_datum_text, collection: media_resource }

      describe "GET /meta-data/:id" do
        let(:path) { "/api-v2/meta-data/#{meta_datum.id}" }
        include_examples :legacy_media_resource_access
      end

      describe "GET /collections/:id/meta-data/:meta_key_id" do
        let(:path) { "/api-v2/collections/#{media_resource.id}/meta-data/#{meta_datum.meta_key_id}" }
        include_examples :legacy_media_resource_access
      end
    end
  end

  describe "not existing resources" do
    let(:unknown_id) { SecureRandom.uuid }

    it "responds 404 for a media-entry" do
      expect(anonymous_client.get("/api-v2/media-entries/#{unknown_id}").status).to eq 404
    end

    it "responds 404 for a collection" do
      expect(anonymous_client.get("/api-v2/collections/#{unknown_id}").status).to eq 404
    end
  end
end
