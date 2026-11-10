require "spec_helper"
require Pathname(File.expand_path("..", __FILE__)).join("shared")

# Admin access variants.
#
# The legacy API does not privilege admins on media-resources: an admin needs
# the same responsibility or permission as every other user. api-v2 grants
# the admin privilege only on routes guarded by `wrap-authorize-admin!`
# (`/api-v2/admin/...`); on user routes being an admin adds nothing.
#
# The read matrix of every user route already runs with the admin variants of
# `ApiAccessParity::GRANTING_CONDITIONS` / `DENYING_CONDITIONS`. This file
# covers what the matrix does not: other authentication methods, write
# scopes, derived listings and the admin routes themselves.

describe "Access parity with the legacy API: admin access" do
  let(:owner) { FactoryBot.create :user }
  let(:admin) { create_admin_user }
  let(:private_entry) { FactoryBot.create :media_entry, responsible_user: owner, get_metadata_and_previews: false }
  let(:private_collection) { FactoryBot.create :collection, responsible_user: owner, get_metadata_and_previews: false }

  def ids_of(response, key, field = "id")
    expect(response.status).to eq ApiAccessParity::GRANTED
    Array(response.body[key]).map { |item| item[field] }
  end

  describe "an admin authenticated by session" do
    it "is denied a private media-entry without permission" do
      expect(session_client_for(admin).get("/api-v2/media-entries/#{private_entry.id}").status).to eq 403
    end

    it "is denied a private collection without permission" do
      expect(session_client_for(admin).get("/api-v2/collections/#{private_collection.id}").status).to eq 403
    end

    it "is granted a private media-entry with a user permission" do
      grant(private_entry, :get_metadata_and_previews, true, user: admin)
      expect(session_client_for(admin).get("/api-v2/media-entries/#{private_entry.id}").status).to eq 200
    end

    it "does not get private media-entries listed" do
      private_entry
      expect(ids_of(session_client_for(admin).get("/api-v2/media-entries/"), "media_entries"))
        .not_to include private_entry.id
    end
  end

  describe "write scopes on user routes" do
    let(:path) { "/api-v2/collections/#{private_collection.id}" }
    let(:body) { {layout: "list"}.to_json }

    def put_as(user)
      client_for(user).put(path) do |req|
        req.body = body
        req.headers["Content-Type"] = "application/json"
      end
    end

    it "PUT /collections/:id responds 403 to an admin without edit permission" do
      expect(put_as(admin).status).to eq 403
    end

    it "PUT /collections/:id responds 403 to an admin with view permission only" do
      FactoryBot.create :collection_user_permission, collection: private_collection, user: admin,
        get_metadata_and_previews: true, edit_metadata_and_relations: false, edit_permissions: false
      expect(put_as(admin).status).to eq 403
    end

    it "PUT /collections/:id grants an admin with edit permission" do
      FactoryBot.create :collection_user_permission, collection: private_collection, user: admin,
        get_metadata_and_previews: true, edit_metadata_and_relations: true, edit_permissions: false
      expect(put_as(admin).status).to eq 200
    end

    it "GET /media-entries/:id/perms/users/ responds 403 to an admin without edit-permissions permission" do
      expect(client_for(admin).get("/api-v2/media-entries/#{private_entry.id}/perms/users/").status).to eq 403
    end

    it "GET /media-entries/:id/perms/users/ grants an admin responsible for the entry" do
      private_entry.update_columns(responsible_user_id: make_admin(owner).id)
      expect(client_for(owner).get("/api-v2/media-entries/#{private_entry.id}/perms/users/").status).to eq 200
    end
  end

  describe "derived listings on user routes" do
    let(:public_collection) { FactoryBot.create :collection, responsible_user: owner, get_metadata_and_previews: true }

    describe "collection-media-entry-arcs" do
      let!(:arc) do
        FactoryBot.create :collection_media_entry_arc, collection: public_collection, media_entry: private_entry
      end

      def listed_entry_ids(user)
        ids_of(client_for(user).get("/api-v2/collection-media-entry-arcs/", collection_id: public_collection.id),
          "collection-media-entry-arcs", "media_entry_id")
      end

      it "are not listed to an admin without permission on the media-entry" do
        expect(listed_entry_ids(admin)).not_to include private_entry.id
      end

      it "are listed to an admin with permission on the media-entry" do
        grant(private_entry, :get_metadata_and_previews, true, user: admin)
        expect(listed_entry_ids(admin)).to include private_entry.id
      end

      it "a single arc is not readable by an admin without permission" do
        expect(client_for(admin).get("/api-v2/collection-media-entry-arcs/#{arc.id}").status).to eq 404
      end
    end

    describe "collection-collection-arcs" do
      before do
        FactoryBot.create :collection_collection_arc, parent: public_collection, child: private_collection
      end

      def listed_child_ids(user)
        ids_of(client_for(user).get("/api-v2/collection-collection-arcs/"),
          "collection-collection-arcs", "child_id")
      end

      it "are not listed to an admin without permission on the child" do
        expect(listed_child_ids(admin)).not_to include private_collection.id
      end

      it "are listed to an admin with permission on the child" do
        grant(private_collection, :get_metadata_and_previews, true, user: admin)
        expect(listed_child_ids(admin)).to include private_collection.id
      end
    end

    describe "full_texts" do
      before do
        ActiveRecord::Base.connection.exec_query(
          "INSERT INTO full_texts (media_resource_id, text) VALUES ($1, $2)",
          "SQL", [private_entry.id, "admin parity text"]
        )
      end

      def searched_ids(user)
        response = client_for(user).get("/api-v2/full_texts/", text: "admin parity text", size: 1000)
        expect(response.status).to eq ApiAccessParity::GRANTED
        Array(response.body.is_a?(Hash) ? response.body["data"] : response.body).map { |ft| ft["media_resource_id"] }
      end

      it "GET /full_texts/:id responds 403 to an admin without permission" do
        expect(client_for(admin).get("/api-v2/full_texts/#{private_entry.id}").status).to eq 403
      end

      it "GET /full_texts/ does not find them for an admin without permission" do
        expect(searched_ids(admin)).not_to include private_entry.id
      end

      it "GET /full_texts/ finds them for an admin with permission" do
        grant(private_entry, :get_metadata_and_previews, true, user: admin)
        expect(searched_ids(admin)).to include private_entry.id
      end
    end
  end

  # No legacy counterpart; the admin privilege is kept on purpose here.
  describe "admin routes keep the admin privilege" do
    it "GET /admin/media-entries/ lists private media-entries" do
      private_entry
      expect(ids_of(client_for(admin).get("/api-v2/admin/media-entries/"), "media_entries"))
        .to include private_entry.id
    end

    it "GET /admin/collections/ lists private collections" do
      private_collection
      expect(ids_of(client_for(admin).get("/api-v2/admin/collections/"), "collections"))
        .to include private_collection.id
    end

    it "GET /admin/media-entries/ responds 403 to a user" do
      expect(client_for(owner).get("/api-v2/admin/media-entries/").status).to eq 403
    end

    it "PUT /admin/collections/:id grants an admin without permission" do
      response = client_for(admin).put("/api-v2/admin/collections/#{private_collection.id}") do |req|
        req.body = {layout: "list"}.to_json
        req.headers["Content-Type"] = "application/json"
      end
      expect(response.status).to eq 200
    end

    it "PUT /admin/collections/:id responds 403 to the responsible user" do
      response = client_for(owner).put("/api-v2/admin/collections/#{private_collection.id}") do |req|
        req.body = {layout: "list"}.to_json
        req.headers["Content-Type"] = "application/json"
      end
      expect(response.status).to eq 403
    end
  end
end
