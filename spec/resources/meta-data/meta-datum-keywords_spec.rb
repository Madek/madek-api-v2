require 'spec_helper'
require Pathname(File.expand_path('..', __FILE__)).join('shared')
require Pathname(File.expand_path('datalayer/spec/models/keyword/terms_for_sorting_shared_context.rb'))

describe 'generated runs' do
  (1..ROUNDS).each do |round|
  #(1..5).each do |round|
    describe "ROUND #{round}" do
      describe 'meta_datum_keywords_for_random_resource_type' do
        include_context :meta_datum_for_random_resource_type
        let(:meta_datum_keywords) { meta_datum('keywords') }

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
          describe 'with random public view permission' do
            before :each do
              media_resource.update! \
                get_metadata_and_previews: (rand <= 0.5)
            end
            describe 'the meta-data resource' do
              #let :resource do
              #  authenticated_json_roa_client.get.relation('meta-datum') \
              #    .get('id' => meta_datum_keywords.id)
              #end

              let :response do
                authenticated_json_client.get("/api/meta-data/#{meta_datum_keywords.id}")
              end

              it 'status, either 200 success or 403 forbidden, '\
                  'corresponds to the get_metadata_and_previews value' do
                expect(response.status).to be == \
                  (media_resource.get_metadata_and_previews ? 200 : 403)
              end

              context 'if the response is 200' do
                let(:value) { response.body['value'] }

                it 'it holds the proper uuid array value' do
                  if response.status == 200
                    value.map { |v| v['id'] }.each do |keyword_id|
                      mdi = response.body['id']
                      expect(MetaDatum::Keyword.find_by(meta_datum_id: mdi,
                                                        keyword_id: keyword_id))
                        .to be
                    end
                  end
                end

                
                it 'it provides valid collection and relations' do
                  if response.status == 200
                    # TODO json roa remove: test valid collection    
                    #resource.collection.each do |c_entry|
                    #  expect(c_entry.get.response.status).to be == 200
                    #  expect(value.map { |v| v['id'] }).to include c_entry.get.data['id']
                    #end

                    # TODO json roa remove: test links: meta-data 2 meta-key
                    meta_key_id = response.body['meta_key_id']
                    expect(authenticated_json_client.get("/api/meta-keys/#{meta_key_id}").status)
                    #expect(resource.relation('meta-key').get.response.status)
                      .to be == 200
                    # TODO json roa remove: test links: meta-data 2 media-entry
                    media_entry_id = response.body['media_entry_id']
                    expect(authenticated_json_client.get("/api/media-entries/#{media_entry_id}").status)
                    #expect(resource.relation('media-entry').get.response.status)
                      .to be == 200
                  end
                end

                context 'sorting of keywords' do
                  include_context :datalayer_terms_for_sorting

                  let(:meta_datum_keywords) do
                    meta_key = FactoryBot.create(:meta_key_keywords,
                                                  keywords_alphabetical_order: true)
                    keywords = terms.reverse.map do |term|
                      FactoryBot.create(:keyword, term: term, meta_key: meta_key)
                    end
                    FactoryBot.create(:meta_datum_keywords,
                                       Hash[:keywords, keywords,
                                            media_resource.model_name.singular, media_resource])
                  end

                  # TODO json roa remove: meta-datum-keywords: alphabet. order fix: check faraday json?
                  it 'the collection is sorted if meta_key.keywords_alphabetical_order true' do
                    if response.status == 200 and value
                      expect(value.map { |v| Keyword.find(v['id']).term }).to be == terms
                    end
                  end
                end
              end
            end
          end
        end
      end
    end
  end
end
