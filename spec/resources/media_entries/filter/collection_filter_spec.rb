require "spec_helper"

describe "filtering media entries" do
  def get_media_entries(filter = nil)
    client.get("/api-v2/media-entries/", filter).body["media_entries"]
  end

  context "by collection_id" do
    include_context :json_client_for_authenticated_token_user do
      it "as single filter option" do
        collection = FactoryBot.create(:collection)
        5.times do
          collection.media_entries << FactoryBot.create(:media_entry)
        end
        get_media_entries("collection_id" => collection.id)
          .each do |me|
          media_entry = MediaEntry.unscoped.find(me["id"])
          expect(collection.media_entries).to include media_entry
        end
      end

      it "combined with other filter option" do
        collection = FactoryBot.create(:collection)
        media_entry_1 = FactoryBot.create(:media_entry,
          get_metadata_and_previews: false)
        media_entry_2 = FactoryBot.create(:media_entry,
          get_metadata_and_previews: true)
        media_entry_3 = FactoryBot.create(:media_entry,
          get_metadata_and_previews: false)
        media_entry_3.user_permissions <<
          FactoryBot.create(:media_entry_user_permission,
            user: user,
            get_metadata_and_previews: true)
        [media_entry_1, media_entry_2, media_entry_3].each do |me|
          collection.media_entries << me
        end

        response = get_media_entries("collection_id" => collection.id,
          "me_get_metadata_and_previews" => true)

        expect(response.count).to be == 2
        response.each do |me|
          media_entry = MediaEntry.unscoped.find(me["id"])
          expect(media_entry).not_to be == media_entry_1
          expect(collection.media_entries).to include media_entry
        end
      end
    end
  end
end
