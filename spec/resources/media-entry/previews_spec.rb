require "spec_helper"

describe "GET /api-v2/media-entries/:id/previews/" do
  let(:media_entry) do
    FactoryBot.create(:media_entry_with_image_media_file,
      get_metadata_and_previews: true)
  end

  let(:response) do
    plain_faraday_json_client.get("/api-v2/media-entries/#{media_entry.id}/previews/")
  end

  it "returns 200" do
    expect(response.status).to eq 200
  end

  it "returns an array" do
    expect(response.body).to be_a Array
  end

  it "each item has only id and thumbnail" do
    response.body.each do |item|
      expect(item.keys.map(&:to_s).sort).to eq ["id", "thumbnail"]
    end
  end

  it "returns all previews for the media entry" do
    expected_ids = media_entry.media_file.previews.map(&:id).map(&:to_s).sort
    actual_ids = response.body.map { |p| p["id"] }.sort
    expect(actual_ids).to eq expected_ids
  end
end
