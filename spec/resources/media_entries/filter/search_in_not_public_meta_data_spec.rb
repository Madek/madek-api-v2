require "spec_helper"
require Pathname(File.expand_path("datalayer/spec/models/media_entry/search_in_not_public_meta_data_shared_context.rb"))

describe "search for media entries with meta data from not public vocabulary" do
  def get_media_entries(filter = nil)
    plain_faraday_json_client.get("/api-v2/media-entries/", filter).body["media_entries"]
  end

  context "applying a meta data filter" do
    include_context "meta data from not public vocabulary shared context"

    it "returns 200 with empty result" do
      filter = {meta_data: [key: meta_key.id]}

      fetched_media_entries =
        get_media_entries("filter_by" => filter.deep_stringify_keys.to_json)
      expect(fetched_media_entries.size).to be == 0
    end
  end
end
