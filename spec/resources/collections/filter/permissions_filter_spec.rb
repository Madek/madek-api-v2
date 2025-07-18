require "spec_helper"
require Pathname(File.expand_path("../../", __FILE__)).join("shared")
require "shared/audit-validator"

describe "filtering collections" do
  include_context :bunch_of_collections

  def get_collections(filter = nil)
    client.get("/api-v2/collections/", filter)
  end

  context "permission params checks" do
    include_context :json_client_for_authenticated_token_user do
      it "returns 422 if some 'me_' not true" do
        response = get_collections("me_get_metadata_and_previews" => false)
        expect(response.status).to be == 422
      end
    end
  end

  context "by public_ permissions" do
    include_context :json_client_for_authenticated_token_user do
      it "public_get_metadata_and_previews" do
        get_collections("public_get_metadata_and_previews" => "true").body["collections"]
          .each do |c|
          collection = Collection.unscoped.find(c["id"])
          expect(collection.get_metadata_and_previews).to be true
        end
      end
    end
  end

  context "by me_ permissons" do
    let :media_entries_relation do
      client.get("media-entries/")
    end

    context "me_get_metadata_and_previews for a user" do
      include_context :json_client_for_authenticated_token_user do
        it "200 for public permissions" do
          10.times {
            FactoryBot.create(:collection,
              get_metadata_and_previews: true)
          }

          get_collections("me_get_metadata_and_previews" => "true").body["collections"]
            .each do |c|
            collection = Collection.unscoped.find(c["id"])
            expect(collection.get_metadata_and_previews).to be true
          end
        end

        it "200 for responsible user" do
          10.times {
            FactoryBot.create(:collection,
              responsible_user: user,
              get_metadata_and_previews: false)
          }

          get_collections("me_get_metadata_and_previews" => "true").body["collections"]
            .each do |c|
            collection = Collection.unscoped.find(c["id"])
            expect(collection.responsible_user).to be == user
          end
        end

        it "200 for user permission" do
          10.times do
            FactoryBot.create \
              :collection_user_permission,
              collection: FactoryBot.create(:collection,
                get_metadata_and_previews: false),
              user: user
          end

          get_collections("me_get_metadata_and_previews" => "true").body["collections"]
            .each do |c|
            collection = Collection.unscoped.find(c["id"])
            expect(collection.user_permissions.first.user).to be == user
          end
        end

        it "200 for group permission" do
          10.times do
            g = FactoryBot.create(:group)
            user.groups << g
            FactoryBot.create \
              :collection_group_permission,
              collection: FactoryBot.create(:collection,
                get_metadata_and_previews: false),
              group: g
          end

          get_collections("me_get_metadata_and_previews" => "true").body["collections"]
            .each do |c|
            collection = Collection.unscoped.find(c["id"])
            expect(user.groups)
              .to include collection.group_permissions.first.group
          end
        end

        describe "get collections with pagination" do
          it "responses with 200" do
            10.times do
              g = FactoryBot.create(:group)
              user.groups << g
              FactoryBot.create \
                :collection_group_permission,
                collection: FactoryBot.create(:collection,
                  get_metadata_and_previews: false),
                group: g
            end

            resp1 = client.get("/api-v2/collections/?page=1&size=5")
            expect(resp1.status).to be == 200
            expect(resp1.body["data"].count).to be 5

            resp2 = client.get("/api-v2/collections/?page=2&size=5")
            expect(resp2.status).to be == 200
            expect(resp2.body["data"].count).to be 5

            expect(lists_of_maps_different?(resp1.body["data"], resp2.body["data"])).to eq true

            resp = client.get("/api-v2/collections/")
            expect(resp.status).to be == 200
            expect(resp.body["collections"].count).to be 10
          end
        end
      end
    end
  end
end
