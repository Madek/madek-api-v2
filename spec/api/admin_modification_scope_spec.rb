require "spec_helper"
require Pathname(File.expand_path("..", __FILE__)).join("shared")

# An admin may modify resources of other users only on `/api-v2/admin/...`
# routes. On user routes an admin needs responsibility or permissions like
# every other user (see spec/api/doc/MASTER-PERMISSION-LOGIC.md,
# "FEATURE-BRANCH-LOGIC").
#
# Every modification on a user route is run by:
# - the responsible user (control: the request is valid and changes the data)
# - an admin without any relation to the resource
# - an admin with a view-only user permission
# The admins must get 403 and the data must stay unchanged.

describe "Admin modifications are restricted to /admin routes" do
  let(:owner) { FactoryBot.create :user }
  let(:admin) { create_admin_user }

  let(:entry) do
    FactoryBot.create :media_entry, responsible_user: owner,
      get_metadata_and_previews: false, get_full_size: false
  end
  let(:collection) do
    FactoryBot.create :collection, responsible_user: owner, get_metadata_and_previews: false
  end
  let(:public_child) { FactoryBot.create :collection, responsible_user: owner, get_metadata_and_previews: true }
  let(:public_entry) { FactoryBot.create :media_entry, responsible_user: owner, get_metadata_and_previews: true }
  let(:successor) { FactoryBot.create :user }
  let(:json_key) { FactoryBot.create(:meta_key_json, id: "test:admin_scope_json").id }

  def send_json(client, method, path, body = nil)
    client.public_send(method, path) do |req|
      req.body = body.to_json unless body.nil?
      req.headers["Content-Type"] = "application/json"
    end
  end

  def full_text_ids
    ActiveRecord::Base.connection.select_values("SELECT media_resource_id FROM full_texts")
  end

  def grant_view_only(resource, user)
    case resource
    when MediaEntry
      FactoryBot.create :media_entry_user_permission, media_entry: resource, user: user,
        get_metadata_and_previews: true, get_full_size: false, edit_metadata: false, edit_permissions: false
    when Collection
      FactoryBot.create :collection_user_permission, collection: resource, user: user,
        get_metadata_and_previews: true, edit_metadata_and_relations: false, edit_permissions: false
    end
  end

  # name => {resource:, request: ->(actor) { [method, path, body] }, changed: ->(actor) { bool }}
  user_route_modifications = {
    "POST /media-entries/:id/meta-data/:meta_key_id/json/ (edit_metadata)" => {
      resource: :entry,
      request: ->(_) { [:post, "/api-v2/media-entries/#{entry.id}/meta-data/#{json_key}/json/", {json: "{}"}] },
      changed: ->(_) { MetaDatum.where(media_entry_id: entry.id, meta_key_id: json_key).exists? }
    },
    "POST /media-entries/:id/full_texts/ (edit_metadata)" => {
      resource: :entry,
      request: ->(_) { [:post, "/api-v2/media-entries/#{entry.id}/full_texts/", {text: "scope probe"}] },
      changed: ->(_) { full_text_ids.include?(entry.id) }
    },
    "POST /media-entries/:id/conf-links/ (edit_permissions)" => {
      resource: :entry,
      request: ->(_) {
        [:post, "/api-v2/media-entries/#{entry.id}/conf-links/",
          {revoked: false, description: "scope probe", expires_at: (Time.zone.now + 1.day)}]
      },
      changed: ->(_) { ConfidentialLink.where(resource_id: entry.id).exists? }
    },
    "PUT /media-entries/:id/perms/resources/ make public (edit_permissions)" => {
      resource: :entry,
      request: ->(_) { [:put, "/api-v2/media-entries/#{entry.id}/perms/resources/", {get_metadata_and_previews: true}] },
      changed: ->(_) { entry.reload.get_metadata_and_previews }
    },
    "PUT /media-entries/:id/perms/resources/ take over responsibility (edit_permissions)" => {
      resource: :entry,
      request: ->(actor) {
        [:put, "/api-v2/media-entries/#{entry.id}/perms/resources/",
          {responsible_user_id: (actor == owner) ? successor.id : actor.id}]
      },
      changed: ->(_) { entry.reload.responsible_user_id != owner.id }
    },
    "POST /media-entries/:id/perms/users/:user_id grant oneself (edit_permissions)" => {
      resource: :entry,
      request: ->(actor) {
        grantee = (actor == owner) ? FactoryBot.create(:user) : actor
        [:post, "/api-v2/media-entries/#{entry.id}/perms/users/#{grantee.id}",
          {get_metadata_and_previews: true, get_full_size: true, edit_metadata: true, edit_permissions: true}]
      },
      changed: ->(_) { Permissions::MediaEntryUserPermission.where(media_entry_id: entry.id, edit_permissions: true).exists? }
    },
    "DELETE /media-entries/:id (edit_permissions)" => {
      resource: :entry,
      request: ->(_) { [:delete, "/api-v2/media-entries/#{entry.id}"] },
      changed: ->(_) { MediaEntry.where(id: entry.id).none? }
    },
    "PUT /collections/:id (edit_metadata_and_relations)" => {
      resource: :collection,
      request: ->(_) { [:put, "/api-v2/collections/#{collection.id}", {layout: "list"}] },
      changed: ->(_) { collection.reload.layout == "list" }
    },
    "POST /collections/:id/collection-arcs/:child_id (edit_metadata_and_relations)" => {
      resource: :collection,
      request: ->(_) { [:post, "/api-v2/collections/#{collection.id}/collection-arcs/#{public_child.id}", {highlight: true}] },
      changed: ->(_) { Arcs::CollectionCollectionArc.where(parent_id: collection.id, child_id: public_child.id).exists? }
    },
    "POST /collections/:id/media-entry-arcs/:media_entry_id (edit_metadata_and_relations)" => {
      resource: :collection,
      request: ->(_) { [:post, "/api-v2/collections/#{collection.id}/media-entry-arcs/#{public_entry.id}", {highlight: true}] },
      changed: ->(_) { Arcs::CollectionMediaEntryArc.where(collection_id: collection.id, media_entry_id: public_entry.id).exists? }
    },
    "PUT /collections/:id/perms/resources/ take over responsibility (edit_permissions)" => {
      resource: :collection,
      request: ->(actor) {
        [:put, "/api-v2/collections/#{collection.id}/perms/resources/",
          {responsible_user_id: (actor == owner) ? successor.id : actor.id}]
      },
      changed: ->(_) { collection.reload.responsible_user_id != owner.id }
    },
    "DELETE /collections/:id (edit_permissions)" => {
      resource: :collection,
      request: ->(_) { [:delete, "/api-v2/collections/#{collection.id}"] },
      changed: ->(_) { Collection.where(id: collection.id).none? }
    }
  }

  describe "user routes" do
    def run_as(actor, op)
      method, path, body = instance_exec(actor, &op[:request])
      send_json(client_for(actor), method, path, body)
    end

    user_route_modifications.each do |name, op|
      describe name do
        it "grants the responsible user (control)" do
          response = run_as(owner, op)
          expect(response.status).to eq(200), "#{response.status}: #{response.body}"
          expect(instance_exec(owner, &op[:changed])).to be true
        end

        it "responds 403 to an admin without relation and changes nothing" do
          expect(run_as(admin, op).status).to eq 403
          expect(instance_exec(admin, &op[:changed])).to be false
        end

        it "responds 403 to an admin with a view-only permission and changes nothing" do
          grant_view_only(send(op[:resource]), admin)
          expect(run_as(admin, op).status).to eq 403
          expect(instance_exec(admin, &op[:changed])).to be false
        end
      end
    end
  end

  describe "admin routes" do
    def expect_admin_only(method, path, body = nil)
      expect(send_json(client_for(owner), method, path, body).status).to eq 403
      expect(yield).to be false
      response = send_json(client_for(admin), method, path, body)
      expect(response.status).to eq(200), "#{response.status}: #{response.body}"
      expect(yield).to be true
    end

    it "PUT /admin/media-entries/:id (soft delete) is denied to the owner and granted to an admin" do
      expect_admin_only(:put, "/api-v2/admin/media-entries/#{entry.id}", {deleted_at: Time.zone.now.iso8601}) do
        MediaEntry.unscoped.find(entry.id).deleted_at.present?
      end
    end

    it "PUT /admin/collections/:id is denied to the owner and granted to an admin" do
      expect_admin_only(:put, "/api-v2/admin/collections/#{collection.id}", {layout: "list"}) do
        collection.reload.layout == "list"
      end
    end

    it "POST /admin/full_texts/:id is denied to the owner and granted to an admin" do
      expect_admin_only(:post, "/api-v2/admin/full_texts/#{entry.id}", {text: "admin text"}) do
        full_text_ids.include?(entry.id)
      end
    end

    describe "with an existing full text" do
      before do
        ActiveRecord::Base.connection.exec_query(
          "INSERT INTO full_texts (media_resource_id, text) VALUES ($1, $2)", "SQL", [entry.id, "old text"]
        )
      end

      def stored_text
        ActiveRecord::Base.connection.select_value(
          "SELECT text FROM full_texts WHERE media_resource_id = $1", "SQL", [entry.id]
        )
      end

      it "PUT /admin/full_texts/:id is denied to the owner and granted to an admin" do
        expect_admin_only(:put, "/api-v2/admin/full_texts/#{entry.id}", {text: "new text"}) do
          stored_text == "new text"
        end
      end

      it "DELETE /admin/full_texts/:id is denied to the owner and granted to an admin" do
        expect_admin_only(:delete, "/api-v2/admin/full_texts/#{entry.id}") { stored_text.nil? }
      end
    end
  end
end
