require "spec_helper"

# What an anonymous (not authenticated) client can read and write on
# media-entries, collections and people. Writes carry a self-made csrf-token,
# which any client can provide. Examples documenting known gaps are pending.

describe "Anonymous access" do
  let(:csrf_token) { "ab63c8ab-a224-441d-9251-8068f6245252" }

  let(:client) do
    Faraday.new(url: api_base_url,
      headers: {accept: "application/json", "Content-Type": "application/json",
                Cookie: "madek-anti-csrf-token=#{csrf_token}", "x-csrf-token": csrf_token}) do |conn|
      conn.response :json, content_type: /\bjson$/
      conn.adapter Faraday.default_adapter
    end
  end

  let(:owner) { FactoryBot.create :user }

  describe "media-entries" do
    let(:public_entry) do
      FactoryBot.create :media_entry, responsible_user: owner, get_metadata_and_previews: true
    end
    let(:private_entry) do
      FactoryBot.create :media_entry, responsible_user: owner, get_metadata_and_previews: false
    end

    context "reading a single entry" do
      it "a public entry is readable" do
        expect(client.get("/api-v2/media-entries/#{public_entry.id}").status).to eq 200
      end

      it "a private entry is not readable" do
        expect(client.get("/api-v2/media-entries/#{private_entry.id}").status).to eq 401
      end

      it "a soft-deleted public entry is not readable" do
        public_entry.update_column(:deleted_at, 1.day.ago)
        expect(client.get("/api-v2/media-entries/#{public_entry.id}").status).to eq 404
      end
    end

    context "index" do
      let(:collection) { FactoryBot.create :collection, responsible_user: owner }

      let(:listed_ids) do
        [public_entry, private_entry].each do |entry|
          FactoryBot.create :collection_media_entry_arc, collection: collection, media_entry: entry
        end
        response = client.get("/api-v2/media-entries/", collection_id: collection.id)
        expect(response.status).to eq 200
        response.body["media_entries"].map { |e| e["id"] }
      end

      it "lists a public entry" do
        expect(listed_ids).to include public_entry.id
      end

      it "does not list a private entry" do
        pending "the index applies permission filters only on request"
        expect(listed_ids).not_to include private_entry.id
      end
    end

    context "meta-data" do
      let(:hidden_vocabulary) { FactoryBot.create :vocabulary, enabled_for_public_view: false }
      let(:hidden_meta_key) { FactoryBot.create :meta_key_text, id: "#{hidden_vocabulary.id}:secret" }

      let!(:public_meta_datum) { FactoryBot.create :meta_datum_text, media_entry: public_entry }
      let!(:hidden_meta_datum) do
        FactoryBot.create :meta_datum_text, media_entry: public_entry, meta_key: hidden_meta_key
      end
      let!(:private_meta_datum) { FactoryBot.create :meta_datum_text, media_entry: private_entry }

      it "of a public entry are readable" do
        expect(client.get("/api-v2/media-entries/#{public_entry.id}/meta-data/").status).to eq 200
        expect(client.get("/api-v2/media-entries/#{public_entry.id}/meta-data/#{public_meta_datum.meta_key_id}")
          .status).to eq 200
      end

      it "of a private entry are not readable" do
        expect(client.get("/api-v2/media-entries/#{private_entry.id}/meta-data/").status).to eq 401
        expect(client.get("/api-v2/media-entries/#{private_entry.id}/meta-data/#{private_meta_datum.meta_key_id}")
          .status).to eq 401
      end

      it "of a non-public vocabulary are not listed" do
        response = client.get("/api-v2/media-entries/#{public_entry.id}/meta-data/")
        expect(response.body.to_json).not_to include hidden_meta_datum.string
      end

      it "of a non-public vocabulary are not readable" do
        pending "the single meta-datum route of media-entries does not check vocabulary permissions"
        response = client.get("/api-v2/media-entries/#{public_entry.id}/meta-data/#{hidden_meta_key.id}")
        expect(response.status).to be_in [403, 404]
      end
    end

    context "permissions of a public entry" do
      before do
        FactoryBot.create :media_entry_user_permission, media_entry: public_entry, user: FactoryBot.create(:user)
      end

      it "the resource permissions are readable" do
        expect(client.get("/api-v2/media-entries/#{public_entry.id}/perms/resources/").status).to eq 200
      end

      it "the users holding permissions are not readable" do
        pending "permission lists only require view rights"
        expect(client.get("/api-v2/media-entries/#{public_entry.id}/perms/users/").status).to be_in [401, 403]
      end
    end

    context "media-files and previews" do
      let(:media_file) { FactoryBot.create :media_file_for_image, media_entry: private_entry }
      let!(:preview) { FactoryBot.create :preview, media_file: media_file, content_type: "image/jpeg" }

      it "the media-file of a private entry is not readable" do
        expect(client.get("/api-v2/media-files/#{media_file.id}").status).to eq 401
      end

      it "the previews of a private entry are not readable" do
        expect(client.get("/api-v2/previews/#{preview.id}").status).to eq 401
      end

      it "the preview list of a private entry is not readable" do
        pending "media-entries/:id/previews/ has no authorization"
        expect(client.get("/api-v2/media-entries/#{private_entry.id}/previews/").status).to be_in [401, 403]
      end

      it "the full-size file of a private entry is not downloadable" do
        expect(client.get("/api-v2/media-entries/#{private_entry.id}/media-files/data-stream/").status)
          .to eq 401
      end
    end

    context "writing on a public entry" do
      let(:meta_key) { FactoryBot.create :meta_key_text }

      it "can not delete the entry" do
        expect(client.delete("/api-v2/media-entries/#{public_entry.id}").status).to eq 401
        expect(MediaEntry.exists?(public_entry.id)).to be true
      end

      it "can not publish the entry" do
        public_entry.update_column(:is_published, false)
        expect(client.put("/api-v2/media-entries/#{public_entry.id}/publish/").status).to eq 401
        expect(public_entry.reload.is_published).to be false
      end

      it "can not add meta-data" do
        url = "/api-v2/media-entries/#{public_entry.id}/meta-data/#{meta_key.id}/texts/"
        expect(client.post(url, {string: "anonymous"}.to_json).status).to eq 401
        expect(MetaDatum.where(media_entry_id: public_entry.id, meta_key_id: meta_key.id)).to be_empty
      end

      it "can not change permissions" do
        url = "/api-v2/media-entries/#{public_entry.id}/perms/resources/get_full_size/true"
        expect(client.put(url).status).to eq 401
        expect(public_entry.reload.get_full_size).to be false
      end

      it "can not create confidential links" do
        url = "/api-v2/media-entries/#{public_entry.id}/conf-links/"
        expect(client.post(url, {revoked: false}.to_json).status).to eq 401
        expect(ConfidentialLink.where(resource_id: public_entry.id)).to be_empty
      end
    end
  end

  describe "collections" do
    let(:public_collection) do
      FactoryBot.create :collection, responsible_user: owner, get_metadata_and_previews: true
    end
    let(:private_collection) do
      FactoryBot.create :collection, responsible_user: owner, get_metadata_and_previews: false
    end

    context "reading a single collection" do
      it "a public collection is readable" do
        expect(client.get("/api-v2/collections/#{public_collection.id}").status).to eq 200
      end

      it "a private collection is not readable" do
        expect(client.get("/api-v2/collections/#{private_collection.id}").status).to eq 401
      end

      it "a soft-deleted public collection is not readable" do
        public_collection.update_column(:deleted_at, 1.day.ago)
        expect(client.get("/api-v2/collections/#{public_collection.id}").status).to eq 404
      end

      it "the meta-data of a private collection are not readable" do
        expect(client.get("/api-v2/collections/#{private_collection.id}/meta-data/").status).to eq 401
      end
    end

    context "index" do
      before do
        public_collection
        private_collection
      end

      let(:listed_ids) do
        response = client.get("/api-v2/collections/", responsible_user_id: owner.id)
        expect(response.status).to eq 200
        response.body["collections"].map { |c| c["id"] }
      end

      it "lists a public collection" do
        expect(listed_ids).to include public_collection.id
      end

      it "does not list a private collection" do
        pending "the index applies permission filters only on request"
        expect(listed_ids).not_to include private_collection.id
      end
    end

    context "contents" do
      let(:private_entry) do
        FactoryBot.create :media_entry, responsible_user: owner, get_metadata_and_previews: false
      end
      let!(:arc) do
        FactoryBot.create :collection_media_entry_arc, collection: private_collection, media_entry: private_entry
      end

      it "the entries of a private collection are not readable" do
        expect(client.get("/api-v2/collections/#{private_collection.id}/media-entry-arcs/").status).to eq 401
      end

      it "the arcs of a private collection are not listed" do
        pending "collection-media-entry-arcs/ has no authorization"
        response = client.get("/api-v2/collection-media-entry-arcs/", collection_id: private_collection.id)
        expect(response.body.to_json).not_to include private_entry.id
      end
    end

    context "writing" do
      let(:entry) { FactoryBot.create :media_entry, responsible_user: owner, get_metadata_and_previews: true }

      it "can not create a collection" do
        expect { expect(client.post("/api-v2/collections/", {}.to_json).status).to eq 401 }
          .not_to change { Collection.count }
      end

      it "can not modify a public collection" do
        url = "/api-v2/collections/#{public_collection.id}"
        expect(client.put(url, {get_metadata_and_previews: false}.to_json).status).to eq 401
        expect(public_collection.reload.get_metadata_and_previews).to be true
      end

      it "can not delete a public collection" do
        expect(client.delete("/api-v2/collections/#{public_collection.id}").status).to eq 401
        expect(Collection.exists?(public_collection.id)).to be true
      end

      it "can not add an entry to a public collection" do
        url = "/api-v2/collections/#{public_collection.id}/media-entry-arcs/#{entry.id}"
        expect(client.post(url, {}.to_json).status).to eq 401
        expect(Arcs::CollectionMediaEntryArc.where(collection_id: public_collection.id)).to be_empty
      end
    end
  end

  describe "people" do
    let(:person) do
      FactoryBot.create :person, admin_comment: "internal note", identification_info: "internal id"
    end

    it "a person is readable" do
      response = client.get("/api-v2/people/#{person.id}")
      expect(response.status).to eq 200
      expect(response.body["last_name"]).to eq person.last_name
    end

    it "internal fields of a person are not readable" do
      pending "people/:id returns admin_comment and identification_info"
      response = client.get("/api-v2/people/#{person.id}")
      expect(response.body.keys).not_to include("admin_comment", "identification_info")
    end

    it "the people index is not readable" do
      expect(client.get("/api-v2/admin/people/").status).to be_in [401, 403]
    end

    it "can not create a person" do
      expect { expect(client.post("/api-v2/admin/people/", {last_name: "x", subtype: "Person"}.to_json).status).to be_in [401, 403] }
        .not_to change { Person.count }
    end

    it "can not modify a person" do
      expect(client.patch("/api-v2/admin/people/#{person.id}", {last_name: "changed"}.to_json).status)
        .to be_in [401, 403]
      expect(person.reload.last_name).not_to eq "changed"
    end

    it "can not delete a person" do
      expect(client.delete("/api-v2/admin/people/#{person.id}").status).to be_in [401, 403]
      expect(Person.exists?(person.id)).to be true
    end
  end
end
