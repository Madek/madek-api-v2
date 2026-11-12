require "spec_helper"
require Pathname(File.expand_path("..", __FILE__)).join("shared")

# Legacy relations: media-entries, collections.
#
# The legacy indexes are not permission-filtered unless `me_*` query params are
# given: they list the ids of every resource (leak), the resources themselves
# are then authorized one by one. api-v2 has to stay at least as strict and
# list exactly the resources the legacy API lets the requester read.

describe "Access parity with the legacy API: indexes" do
  let(:owner) { FactoryBot.create :user }

  shared_examples :legacy_index_access do
    def listed_ids(condition)
      response = client_under(condition, media_resource).get(path)
      expect(response.status).to eq ApiAccessParity::GRANTED
      response.body[index_key].map { |resource| resource["id"] }
    end

    context "when the resource is public" do
      before { media_resource.update_columns(get_metadata_and_previews: true) }

      %i[anonymous unrelated_user admin_without_permission].each do |condition|
        it "lists it for #{condition}" do
          expect(listed_ids(condition)).to include media_resource.id
        end
      end
    end

    context "when the resource is not public" do
      before { media_resource.update_columns(get_metadata_and_previews: false) }

      ([:anonymous] + ApiAccessParity::DENYING_CONDITIONS).each do |condition|
        it "does not list it for #{condition}" do
          expect(listed_ids(condition)).not_to include media_resource.id
        end
      end

      ApiAccessParity::GRANTING_CONDITIONS.each do |condition|
        it "lists it for #{condition}" do
          expect(listed_ids(condition)).to include media_resource.id
        end
      end
    end
  end

  describe "GET /media-entries/" do
    let(:media_resource) { FactoryBot.create :media_entry, responsible_user: owner }
    let(:path) { "/api-v2/media-entries/" }
    let(:index_key) { "media_entries" }
    include_examples :legacy_index_access
  end

  describe "GET /collections/" do
    let(:media_resource) { FactoryBot.create :collection, responsible_user: owner }
    let(:path) { "/api-v2/collections/" }
    let(:index_key) { "collections" }
    include_examples :legacy_index_access
  end
end
