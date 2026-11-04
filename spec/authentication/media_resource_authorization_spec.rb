require "spec_helper"

# Authorization on media resources. Examples documenting known gaps are pending
# until fixed; the remaining examples are positive controls.

describe "Media resource authorization" do
  let(:csrf_token) { "ab63c8ab-a224-441d-9251-8068f6245252" }

  def json_client(headers = {})
    Faraday.new(url: api_base_url,
      headers: {accept: "application/json", "Content-Type": "application/json"}.merge(headers)) do |conn|
      conn.response :json, content_type: /\bjson$/
      conn.adapter Faraday.default_adapter
    end
  end

  def session_client(user)
    user_session = UserSession.create!(user: user, auth_system: AuthSystem.first.presence,
      token_hash: "hashimotio", created_at: Time.now)
    json_client(
      Cookie: "#{Madek::Constants::MADEK_SESSION_COOKIE_NAME}=#{user_session.token}; " \
              "madek-anti-csrf-token=#{csrf_token}",
      "x-csrf-token": csrf_token
    )
  end

  let(:owner) { FactoryBot.create :user }
  let(:other_user) { FactoryBot.create :user }

  let(:owner_client) { session_client(owner) }
  let(:other_user_client) { session_client(other_user) }
  let(:anonymous_client) do
    json_client(Cookie: "madek-anti-csrf-token=#{csrf_token}", "x-csrf-token": csrf_token)
  end

  describe "collection-collection arcs require edit rights on the parent" do
    let(:parent) { FactoryBot.create :collection, responsible_user: owner }
    let(:child) { FactoryBot.create :collection, responsible_user: owner }
    let(:url) { "/api-v2/collections/#{parent.id}/collection-arcs/#{child.id}" }

    def arc_exists?
      Arcs::CollectionCollectionArc.exists?(parent_id: parent.id, child_id: child.id)
    end

    context "POST" do
      it "succeeds for the owner" do
        expect(owner_client.post(url, {}.to_json).status).to eq 200
        expect(arc_exists?).to be true
      end

      it "is forbidden for another user" do
        expect(other_user_client.post(url, {}.to_json).status).to eq 403
        expect(arc_exists?).to be false
      end

      it "is forbidden for anonymous" do
        expect(anonymous_client.post(url, {}.to_json).status).to be_in [401, 403]
        expect(arc_exists?).to be false
      end
    end

    context "POST with a child the client cannot view" do
      let(:child) { FactoryBot.create :collection, responsible_user: other_user }

      it "is forbidden for the owner of the parent" do
        expect(owner_client.post(url, {}.to_json).status).to eq 403
        expect(arc_exists?).to be false
      end
    end

    context "DELETE" do
      before { FactoryBot.create :collection_collection_arc, parent: parent, child: child }

      it "succeeds for the owner" do
        expect(owner_client.delete(url).status).to eq 200
        expect(arc_exists?).to be false
      end

      it "is forbidden for another user" do
        expect(other_user_client.delete(url).status).to eq 403
        expect(arc_exists?).to be true
      end

      it "is forbidden for anonymous" do
        expect(anonymous_client.delete(url).status).to be_in [401, 403]
        expect(arc_exists?).to be true
      end
    end
  end

  shared_examples :meta_data_delete_requires_edit_rights do
    let!(:meta_datum) { FactoryBot.create :meta_datum_text, resource_key => resource }
    let(:url) { "/api-v2/#{resource_path}/#{resource.id}/meta-data/#{meta_datum.meta_key_id}" }

    it "succeeds for the owner" do
      expect(owner_client.delete(url).status).to eq 200
      expect(MetaDatum.exists?(meta_datum.id)).to be false
    end

    it "is forbidden for another user who can only view" do
      expect(other_user_client.delete(url).status).to eq 403
      expect(MetaDatum.exists?(meta_datum.id)).to be true
    end

    it "is forbidden for anonymous on a public resource" do
      expect(anonymous_client.delete(url).status).to be_in [401, 403]
      expect(MetaDatum.exists?(meta_datum.id)).to be true
    end
  end

  describe "deleting meta-data requires edit rights, not only view rights" do
    context "media-entries" do
      let(:resource) do
        FactoryBot.create :media_entry, responsible_user: owner, get_metadata_and_previews: true
      end
      let(:resource_key) { :media_entry }
      let(:resource_path) { "media-entries" }

      include_examples :meta_data_delete_requires_edit_rights
    end

    context "collections" do
      let(:resource) do
        FactoryBot.create :collection, responsible_user: owner, get_metadata_and_previews: true
      end
      let(:resource_key) { :collection }
      let(:resource_path) { "collections" }

      include_examples :meta_data_delete_requires_edit_rights
    end
  end

  describe "deleting meta-data-people is scoped to the authorized media-entry" do
    let(:own_entry) { FactoryBot.create :media_entry, responsible_user: other_user }
    let!(:own_meta_datum) { FactoryBot.create :meta_datum_people, media_entry: own_entry }
    let(:meta_key_id) { own_meta_datum.meta_key_id }

    let(:foreign_entry) { FactoryBot.create :media_entry, responsible_user: owner }
    let!(:foreign_meta_datum) { FactoryBot.create :meta_datum_people, media_entry: foreign_entry }

    def delete_url(meta_datum_person_id)
      "/api-v2/media-entries/#{own_entry.id}/meta-data/#{meta_key_id}/meta-data-people/#{meta_datum_person_id}"
    end

    it "succeeds for an entry of one's own" do
      own_person = own_meta_datum.meta_data_people.first
      expect(other_user_client.delete(delete_url(own_person.id)).status).to eq 200
      expect(MetaDatum::Person.exists?(own_person.id)).to be false
    end

    it "does not delete meta-data-people of a foreign entry" do
      foreign_person = foreign_meta_datum.meta_data_people.first
      other_user_client.delete(delete_url(foreign_person.id))
      expect(MetaDatum::Person.exists?(foreign_person.id)).to be true
    end
  end

  shared_examples :conf_links_scoped_to_resource do
    let(:own_link) { FactoryBot.create :confidential_link, resource: own_resource, user: other_user }
    let!(:foreign_link) do
      FactoryBot.create :confidential_link, resource: foreign_resource, user: owner, revoked: true
    end

    def link_url(link)
      "/api-v2/#{resource_path}/#{own_resource.id}/conf-links/#{link.id}"
    end

    it "GET succeeds for a link of one's own resource" do
      expect(other_user_client.get(link_url(own_link)).status).to eq 200
    end

    it "GET does not return a link of a foreign resource" do
      expect(other_user_client.get(link_url(foreign_link)).status).to be_in [403, 404]
    end

    it "PUT does not modify a link of a foreign resource" do
      other_user_client.put(link_url(foreign_link), {revoked: false}.to_json)
      expect(foreign_link.reload.revoked).to be true
    end

    it "DELETE does not delete a link of a foreign resource" do
      other_user_client.delete(link_url(foreign_link))
      expect(ConfidentialLink.exists?(foreign_link.id)).to be true
    end
  end

  describe "confidential links are scoped to the authorized resource" do
    context "media-entries" do
      let(:own_resource) { FactoryBot.create :media_entry, responsible_user: other_user }
      let(:foreign_resource) { FactoryBot.create :media_entry, responsible_user: owner }
      let(:resource_path) { "media-entries" }

      include_examples :conf_links_scoped_to_resource
    end

    context "collections" do
      let(:own_resource) { FactoryBot.create :collection, responsible_user: other_user }
      let(:foreign_resource) { FactoryBot.create :collection, responsible_user: owner }
      let(:resource_path) { "collections" }

      include_examples :conf_links_scoped_to_resource
    end
  end

  describe "indexes only list viewable resources" do
    context "media-entries" do
      let!(:public_entry) do
        FactoryBot.create :media_entry, responsible_user: owner, get_metadata_and_previews: true
      end
      let!(:private_entry) do
        FactoryBot.create :media_entry, responsible_user: owner, get_metadata_and_previews: false
      end

      let(:collection) { FactoryBot.create :collection, responsible_user: owner }

      before do
        [public_entry, private_entry].each do |entry|
          FactoryBot.create :collection_media_entry_arc, collection: collection, media_entry: entry
        end
      end

      let(:listed_ids) do
        response = anonymous_client.get("/api-v2/media-entries/", collection_id: collection.id)
        expect(response.status).to eq 200
        response.body["media_entries"].map { |e| e["id"] }
      end

      it "lists a public entry" do
        expect(listed_ids).to include public_entry.id
      end

      it "does not list a private entry" do
        expect(listed_ids).not_to include private_entry.id
      end

      def listed_ids_for(client)
        response = client.get("/api-v2/media-entries/", collection_id: collection.id)
        expect(response.status).to eq 200
        response.body["media_entries"].map { |e| e["id"] }
      end

      it "lists a private entry for its responsible user" do
        expect(listed_ids_for(owner_client)).to include private_entry.id
      end

      it "does not list a private entry for another user" do
        expect(listed_ids_for(other_user_client)).not_to include private_entry.id
      end

      it "lists a private entry for a user with view permission" do
        FactoryBot.create :media_entry_user_permission, media_entry: private_entry, user: other_user,
          get_metadata_and_previews: true, get_full_size: false, edit_metadata: false, edit_permissions: false
        expect(listed_ids_for(other_user_client)).to include private_entry.id
      end
    end

    context "collections" do
      let!(:public_collection) do
        FactoryBot.create :collection, responsible_user: owner, get_metadata_and_previews: true
      end
      let!(:private_collection) do
        FactoryBot.create :collection, responsible_user: owner, get_metadata_and_previews: false
      end

      let(:listed_ids) do
        response = anonymous_client.get("/api-v2/collections/", responsible_user_id: owner.id)
        expect(response.status).to eq 200
        response.body["collections"].map { |c| c["id"] }
      end

      it "lists a public collection" do
        expect(listed_ids).to include public_collection.id
      end

      it "does not list a private collection" do
        expect(listed_ids).not_to include private_collection.id
      end

      def listed_ids_for(client)
        response = client.get("/api-v2/collections/", responsible_user_id: owner.id)
        expect(response.status).to eq 200
        response.body["collections"].map { |c| c["id"] }
      end

      it "lists a private collection for its responsible user" do
        expect(listed_ids_for(owner_client)).to include private_collection.id
      end

      it "does not list a private collection for another user" do
        expect(listed_ids_for(other_user_client)).not_to include private_collection.id
      end

      it "lists a private collection for a user with view permission" do
        FactoryBot.create :collection_user_permission, collection: private_collection, user: other_user,
          get_metadata_and_previews: true, edit_metadata_and_relations: false, edit_permissions: false
        expect(listed_ids_for(other_user_client)).to include private_collection.id
      end
    end
  end

  describe "meta-data writes require use rights on the vocabulary" do
    let(:entry) { FactoryBot.create :media_entry, responsible_user: owner }
    let(:vocabulary) do
      FactoryBot.create :vocabulary, enabled_for_public_view: true, enabled_for_public_use: false
    end
    let(:meta_key) { FactoryBot.create :meta_key_text, id: "#{vocabulary.id}:restricted" }
    let(:url) { "/api-v2/media-entries/#{entry.id}/meta-data/#{meta_key.id}/texts/" }

    def meta_datum_exists?
      MetaDatum.exists?(media_entry_id: entry.id, meta_key_id: meta_key.id)
    end

    it "is forbidden without use permission" do
      expect(owner_client.post(url, {string: "text"}.to_json).status).to eq 403
      expect(meta_datum_exists?).to be false
    end

    it "succeeds with use permission" do
      FactoryBot.create :vocabulary_user_permission, vocabulary: vocabulary, user: owner, use: true, view: true
      expect(owner_client.post(url, {string: "text"}.to_json).status).to eq 200
      expect(meta_datum_exists?).to be true
    end

    it "succeeds for a publicly usable vocabulary" do
      vocabulary.update!(enabled_for_public_use: true)
      expect(owner_client.post(url, {string: "text"}.to_json).status).to eq 200
      expect(meta_datum_exists?).to be true
    end
  end

  describe "full texts are only readable with view rights" do
    let(:public_entry) do
      FactoryBot.create :media_entry, responsible_user: owner, get_metadata_and_previews: true
    end
    let(:private_entry) do
      FactoryBot.create :media_entry, responsible_user: owner, get_metadata_and_previews: false
    end

    before do
      [public_entry, private_entry].each do |entry|
        ActiveRecord::Base.connection.exec_query(
          "INSERT INTO full_texts (media_resource_id, text) VALUES ($1, $2)",
          "SQL", [entry.id, "secret text of #{entry.id}"]
        )
      end
    end

    it "the full text of a public entry is readable" do
      expect(anonymous_client.get("/api-v2/full_texts/#{public_entry.id}").status).to eq 200
    end

    it "the full text of a private entry is not readable" do
      expect(anonymous_client.get("/api-v2/full_texts/#{private_entry.id}").status).to be_in [401, 403, 404]
    end

    it "the full text of a private entry is readable by its responsible user" do
      expect(owner_client.get("/api-v2/full_texts/#{private_entry.id}").status).to eq 200
    end

    it "the full text of a private entry is not readable by another user" do
      expect(other_user_client.get("/api-v2/full_texts/#{private_entry.id}").status).to eq 403
    end

    it "the full text of a private entry is searchable by its responsible user" do
      response = owner_client.get("/api-v2/full_texts/", text: "secret text", size: 1000)
      ids = Array(response.body.is_a?(Hash) ? response.body["data"] : response.body).map { |ft| ft["media_resource_id"] }
      expect(ids).to include private_entry.id, public_entry.id
    end

    it "the full text of a private entry is not searchable" do
      response = anonymous_client.get("/api-v2/full_texts/", text: "secret text", size: 1000)
      ids = Array(response.body.is_a?(Hash) ? response.body["data"] : response.body).map { |ft| ft["media_resource_id"] }
      expect(ids).not_to include private_entry.id
    end
  end

  describe "permission lists require edit-permission rights" do
    let(:entry) { FactoryBot.create :media_entry, responsible_user: owner, get_metadata_and_previews: true }
    let(:url) { "/api-v2/media-entries/#{entry.id}/perms/users/" }

    it "are readable by the responsible user" do
      expect(owner_client.get(url).status).to eq 200
    end

    it "are not readable by a user with view rights only" do
      expect(other_user_client.get(url).status).to eq 403
    end
  end

  describe "collection-media-entry arcs" do
    let(:collection) { FactoryBot.create :collection, responsible_user: owner }
    let(:private_entry) { FactoryBot.create :media_entry, responsible_user: owner, get_metadata_and_previews: false }
    let!(:arc) do
      FactoryBot.create :collection_media_entry_arc, collection: collection, media_entry: private_entry
    end

    def listed_entry_ids(client)
      response = client.get("/api-v2/collection-media-entry-arcs/", collection_id: collection.id)
      Array(response.body["collection-media-entry-arcs"]).map { |a| a["media_entry_id"] }
    end

    it "are listed for the responsible user" do
      expect(listed_entry_ids(owner_client)).to include private_entry.id
    end

    it "are not listed for another user" do
      expect(listed_entry_ids(other_user_client)).not_to include private_entry.id
    end

    it "a single arc is not readable by another user" do
      expect(other_user_client.get("/api-v2/collection-media-entry-arcs/#{arc.id}").status).to eq 404
    end

    context "creating an arc requires view rights on the media-entry" do
      let(:own_collection) { FactoryBot.create :collection, responsible_user: other_user }
      let(:url) { "/api-v2/collections/#{own_collection.id}/media-entry-arcs/#{private_entry.id}" }

      it "is forbidden for an entry the user may not view" do
        expect(other_user_client.post(url, {}.to_json).status).to eq 403
        expect(Arcs::CollectionMediaEntryArc.where(collection_id: own_collection.id)).to be_empty
      end

      it "succeeds for a viewable entry" do
        private_entry.update_column(:get_metadata_and_previews, true)
        expect(other_user_client.post(url, {}.to_json).status).to eq 200
      end
    end
  end

  describe "favoring requires view rights" do
    let(:private_entry) { FactoryBot.create :media_entry, responsible_user: owner, get_metadata_and_previews: false }
    let(:private_collection) { FactoryBot.create :collection, responsible_user: owner, get_metadata_and_previews: false }

    it "a private media-entry can not be favored by another user" do
      expect(other_user_client.post("/api-v2/media-entry/#{private_entry.id}/favorites/").status).to eq 403
    end

    it "a private media-entry can be favored by its responsible user" do
      expect(owner_client.post("/api-v2/media-entry/#{private_entry.id}/favorites/").status).to eq 200
    end

    it "a private collection can not be favored by another user" do
      expect(other_user_client.post("/api-v2/collections/#{private_collection.id}/favorites").status).to eq 403
    end

    it "a private collection can be favored by its responsible user" do
      expect(owner_client.post("/api-v2/collections/#{private_collection.id}/favorites").status).to eq 200
    end

    it "the favorites of the user are listed" do
      owner_client.post("/api-v2/media-entry/#{private_entry.id}/favorites/")
      response = owner_client.get("/api-v2/favorites/media-entries/")
      expect(response.status).to eq 200
      expect(response.body["media_entry_ids"]).to eq [private_entry.id]
    end
  end
end
