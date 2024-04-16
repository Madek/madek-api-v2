require 'spec_helper'
require 'json'
require Pathname(File.expand_path('../..', __FILE__)).join('shared')

describe 'generated runs' do
  (1..ROUNDS).each do |round|
  #(1..1).each do |round|
    describe "ROUND #{round}" do
      describe 'meta_datum_json_for_random_resource_type' do
        include_context :meta_datum_for_random_resource_type
        let(:meta_datum_json) { meta_datum "json" }

        describe 'authenticated_json_client' do
          include_context :authenticated_json_client

          after :each do |example|
            if example.exception
              example.exception.message << \
                "\n  MediaResource: #{media_resource} " \
                " #{media_resource.attributes}"
              example.exception.message << "\n  Client: #{client_entity} " \
                " #{client_entity.attributes}"
            end
          end

          describe 'with creator is authed user' do
            before :each do
              media_resource.update! \
                creator_id: client_entity.id,
                responsible_user_id: client_entity.id
            end

            describe 'the meta-datum resource' do
              let :response do
                url = ''
                if meta_datum_json.media_entry_id == media_resource.id
                  url = "/api/media-entry/#{meta_datum_json.media_entry_id}/meta-datum/#{meta_datum_json.meta_key_id}"
                end
                if meta_datum_json.collection_id == media_resource.id
                  url = "/api/collection/#{meta_datum_json.collection_id}/meta-datum/#{meta_datum_json.meta_key_id}"
                end
                authenticated_json_client.get(url)
              end

              it 'status 200' do
                expect(response.status).to be == 200
              end

              let :json_value do
                response.body['meta-data']['json']
              end

              it 'holds the proper json value' do
                  expect(json_value).to be == meta_datum_json.json
              end
            end

          end
        end
      end
    end
  end
end
