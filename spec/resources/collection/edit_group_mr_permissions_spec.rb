require "spec_helper"
require "shared/audit-validator"
require Pathname(File.expand_path("..", __FILE__)).join("shared")
require Pathname(File.expand_path("../../", __FILE__)).join("audits/shared")

describe "Editing collection permissions on user routes" do
  let!(:user_token) { ApiToken.create(user: user, scope_read: true, scope_write: true, description: "token") }

  let(:collection) do
    collection = FactoryBot.create(:collection, get_metadata_and_previews: true)
    FactoryBot.create(:meta_datum_text, value: "gaura nitai bol", collection: collection)
    collection
  end

  let(:collection_url) { "/api-v2/collections/#{collection.id}" }

  def get(path) = wtoken_header_plain_faraday_json_client_get(user_token.token, path)

  def post(path, body) = wtoken_header_plain_faraday_json_client_post(user_token.token, path, body: body)

  def put(path, body = nil) = wtoken_header_plain_faraday_json_client_put(user_token.token, path, body: body)

  def delete(path) = wtoken_header_plain_faraday_json_client_delete(user_token.token, path)

  ResourceAccess::ROLES.each_key do |role|
    context "as #{role}" do
      let!(:user) { FactoryBot.create((role == :admin) ? :admin_user : :user, password: "password", notes: "user") }

      ResourceAccess::GRANTING_VARIANTS.each do |variant|
        context "with access via #{variant}" do
          before { ResourceAccess.setup(collection, user, variant) }

          it "creates, updates & delete group-permission for collection" do
            expect(get(collection_url).status).to eq 200

            group2 = FactoryBot.create(:group)
            body = {"get_metadata_and_previews" => true, "edit_metadata_and_relations" => true}
            expect(post("#{collection_url}/perms/groups/#{group2.id}", body).status).to eq 200
            expect(get("#{collection_url}/perms/groups/#{group2.id}").status).to eq 200

            response = put("#{collection_url}/perms/groups/#{group2.id}/get_metadata_and_previews/false")
            expect(response.status).to eq 200
            expect(response.body["get_metadata_and_previews"]).to eq false

            expect(get("#{collection_url}/perms/groups/").status).to eq 200
            expect(delete("#{collection_url}/perms/groups/#{group2.id}").status).to eq 200
          end

          it "creates, updates & delete user-permission for collection" do
            expect(get(collection_url).status).to eq 200

            user2 = FactoryBot.create(:user)
            body = {"get_metadata_and_previews" => true, "edit_metadata_and_relations" => true, "edit_permissions" => true}
            expect(post("#{collection_url}/perms/users/#{user2.id}", body).status).to eq 200
            expect(get("#{collection_url}/perms/users/#{user2.id}").status).to eq 200

            response = put("#{collection_url}/perms/users/#{user2.id}/get_metadata_and_previews/false")
            expect(response.status).to eq 200
            expect(response.body["get_metadata_and_previews"]).to eq false

            expect(get("#{collection_url}/perms/users/").status).to eq 200
            expect(delete("#{collection_url}/perms/users/#{user2.id}").status).to eq 200
          end

          it "get & updates resource-perms for collection" do
            expect(get("#{collection_url}/perms/resources/").status).to eq 200

            data = {
              "get_metadata_and_previews" => true,
              "responsible_user_id" => collection.reload.responsible_user_id,
              "clipboard_user_id" => nil,
              "responsible_delegation_id" => nil
            }
            expect(put("#{collection_url}/perms/resources/", data).status).to eq 200

            response = put("#{collection_url}/perms/resources/get_metadata_and_previews/false")
            expect(response.status).to eq 200
            expect(response.body["get_metadata_and_previews"]).to eq false
          end

          # Only the responsible user (or delegation member) may change the
          # responsibility; edit_permissions alone is not enough, also for admins.
          it "changes the responsibility only when responsible" do
            other_user = FactoryBot.create(:user)
            data = {
              "get_metadata_and_previews" => true,
              "responsible_user_id" => (variant == :responsible_user) ? other_user.id : user.id,
              "clipboard_user_id" => nil,
              "responsible_delegation_id" => nil
            }
            responsible_user_id = collection.reload.responsible_user_id
            response = put("#{collection_url}/perms/resources/", data)

            if variant == :responsible_user
              expect(response.status).to eq 200
              expect(collection.reload.responsible_user_id).to eq other_user.id
            else
              expect(response.status).to eq 403
              expect(collection.reload.responsible_user_id).to eq responsible_user_id
            end
          end
        end
      end

      context "without permissions" do
        before { ResourceAccess.setup(collection, user, ResourceAccess::DENYING_VARIANT) }

        let(:group2) { FactoryBot.create(:group) }
        let(:user2) { FactoryBot.create(:user) }

        it "can read the public collection" do
          expect(get(collection_url).status).to eq 200
        end

        it "is forbidden to read or edit group-permissions" do
          FactoryBot.create(:collection_group_permission, collection: collection, group: group2,
            get_metadata_and_previews: true, edit_metadata_and_relations: false)

          expect(post("#{collection_url}/perms/groups/#{FactoryBot.create(:group).id}",
            {"get_metadata_and_previews" => true, "edit_metadata_and_relations" => true}).status).to eq 403
          expect(get("#{collection_url}/perms/groups/#{group2.id}").status).to eq 403
          expect(get("#{collection_url}/perms/groups/").status).to eq 403
          expect(put("#{collection_url}/perms/groups/#{group2.id}/get_metadata_and_previews/false").status).to eq 403
          expect(delete("#{collection_url}/perms/groups/#{group2.id}").status).to eq 403
          expect(collection.group_permissions.find_by(group: group2).get_metadata_and_previews).to eq true
        end

        it "is forbidden to read or edit user-permissions" do
          FactoryBot.create(:collection_user_permission, collection: collection, user: user2,
            get_metadata_and_previews: true, edit_metadata_and_relations: false, edit_permissions: false)

          expect(post("#{collection_url}/perms/users/#{user.id}",
            {"get_metadata_and_previews" => true, "edit_metadata_and_relations" => true,
             "edit_permissions" => true}).status).to eq 403
          expect(get("#{collection_url}/perms/users/#{user2.id}").status).to eq 403
          expect(get("#{collection_url}/perms/users/").status).to eq 403
          expect(put("#{collection_url}/perms/users/#{user2.id}/get_metadata_and_previews/false").status).to eq 403
          expect(delete("#{collection_url}/perms/users/#{user2.id}").status).to eq 403
          expect(collection.user_permissions.find_by(user: user)).to be_nil
        end

        it "can read but not edit resource-perms and cannot take over responsibility" do
          responsible_user_id = collection.responsible_user_id

          # resource-level perms only need view permission (permissions/get.clj)
          expect(get("#{collection_url}/perms/resources/").status).to eq 200
          expect(put("#{collection_url}/perms/resources/",
            {"get_metadata_and_previews" => true, "responsible_user_id" => user.id,
             "clipboard_user_id" => nil, "responsible_delegation_id" => nil}).status).to eq 403
          expect(put("#{collection_url}/perms/resources/get_metadata_and_previews/false").status).to eq 403

          collection.reload
          expect(collection.responsible_user_id).to eq responsible_user_id
          expect(collection.get_metadata_and_previews).to eq true
        end
      end
    end
  end
end
