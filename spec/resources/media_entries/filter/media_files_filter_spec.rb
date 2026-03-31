require "spec_helper"

describe "filtering media entries by media_files attributes" do
  let(:target_entry) { FactoryBot.create(:media_entry_with_image_media_file) }
  let(:other_entry) { FactoryBot.create(:media_entry_with_image_media_file) }

  def get_media_entries(filter)
    plain_faraday_json_client
      .get("/api-v2/media-entries/", "filter_by" => filter.to_json)
  end

  before do
    target_entry
    other_entry
  end

  context "with a valid key" do
    it "filters by content_type" do
      filter = {media_files: [{key: "content_type",
                               value: target_entry.media_file.content_type}]}
      response = get_media_entries(filter)
      expect(response.status).to eq 200
      ids = response.body["media_entries"].map { |me| me["id"] }
      expect(ids).to include target_entry.id
    end

    it "filters by extension" do
      filter = {media_files: [{key: "extension",
                               value: target_entry.media_file.extension}]}
      response = get_media_entries(filter)
      expect(response.status).to eq 200
      ids = response.body["media_entries"].map { |me| me["id"] }
      expect(ids).to include target_entry.id
    end

    it "filters by media_type" do
      filter = {media_files: [{key: "media_type",
                               value: target_entry.media_file.media_type}]}
      response = get_media_entries(filter)
      expect(response.status).to eq 200
      ids = response.body["media_entries"].map { |me| me["id"] }
      expect(ids).to include target_entry.id
    end
  end

  context "with an invalid key" do
    it "returns status 500 for an alphanumeric key when field does not exist" do
      filter = {media_files: [{key: "bogus", value: "anything"}]}
      response = get_media_entries(filter)
      expect(response.status).to eq 500
    end

    it "returns status 422 for a non-alphanumeric key" do
      filter = {media_files: [{key: "pg_sleep(1)--", value: "x"}]}
      response = get_media_entries(filter)
      expect(response.status).to eq 422
    end
  end
end
