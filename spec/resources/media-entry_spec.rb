require "spec_helper"
require Pathname(File.expand_path("..", __FILE__)).join("media-entry", "shared")

shared_examples :check_data_includes_excatly_the_keys do |keys|
  it "the data includes exactly the keys #{keys}" do
    expect(data.keys.map(&:to_sym).sort).to \
      be == keys.map(&:to_sym).sort
  end
end

shared_context :with_public_preview_and_metadata_permission do
  before :each do
    media_entry.update! get_metadata_and_previews: true
  end
end

shared_context :check_success_and_data_with_public_permission do
  context "with public preview and metadata permission" do
    include_context :with_public_preview_and_metadata_permission

    it "succeeds 200" do
      expect(response.status).to be == 200
    end

    context "the body" do
      let :data do
        response.body
      end

      include_examples :check_data_includes_excatly_the_keys,
        [:created_at, :id, :is_published,
          :creator_id,
          :responsible_user_id,
          :updated_at, :edit_session_updated_at,
          :meta_data_updated_at]
    end
  end
end

shared_context :content_type_part do
  let :content_type do
    response.headers["content-type"].split(";").first
  end
end

context "Getting a media-entry resource without authentication" do
  let :media_entry do
    FactoryBot.create :media_entry
  end

  include_context :check_media_entry_resource_via_any,
    :check_success_and_data_with_public_permission

  context "with public preview and metadata permission" do
    include_context :with_public_preview_and_metadata_permission

    context "for plain json" do
      include_context :media_entry_resource_via_plain_json
      describe "the content-type part of the content-type header" do
        include_context :content_type_part
        it do
          expect(content_type).to be == "application/json"
        end
      end
    end
  end
end
