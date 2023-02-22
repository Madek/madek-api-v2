require 'spec_helper'

describe 'Getting the children of a collection' do
  context 'existing children' do
    let :collection_with_children do
      collection = FactoryBot.create(:collection,
                                      get_metadata_and_previews: true)
      collection.media_entries << (@me = FactoryBot.create(:media_entry, get_metadata_and_previews: true))
      collection.collections << FactoryBot.create(:collection)
      collection
    end

    #let :json_roa_keyword_resource do
    #  JSON_ROA::Client.connect \
    #    "#{api_base_url}/collections/#{collection_with_children.id}"
    #end

    def cme_query(filter)
      plain_faraday_json_client.get("/api/collection-media-entry-arcs/", filter)
    end

    def cme_query_col(id)
      cme_query('collection_id' => id)
    end

    def cme_query_me(id)
      cme_query('media_entry_id' => id)
    end
    
    # TODO json roa remove: test links: collection children relation media-entries
    #it 'responds with 200 for media-entries' do
    #  relation = json_roa_keyword_resource.get.relation('collections').get
    #  expect(relation.response.status).to be == 200
    #  expect(relation.data['collections'].length).to be == 1
    #end

    # TODO json roa remove: test links: collection children relation collections
    it 'responds with 200 for collections' do
      expect(cme_query_col(collection_with_children.id).body.length).to be == 1
    #  relation = json_roa_keyword_resource.get.relation('media-entries').get
    #  expect(relation.response.status).to be == 200
    #  expect(relation.data['media-entries'].length).to be == 1
    end

    # TODO json roa remove: test links: collection children relation collection-media-entry-arcs
    context 'collection-media-entry-arcs' do

      it 'is accessible' do
        expect(cme_query_col(collection_with_children.id).body.length).to be == 1
    #    relation = json_roa_keyword_resource.get.relation('collection-media-entry-arcs').get
    #    expect(relation.response.status).to be == 200
    #    expect(relation.data['collection-media-entry-arcs'].length).to be == 1
      end

      it 'links for a round trip "collection → arc → entry → arc → collection" ' \
        ' can be traversed and retrieve the original collection' do
          media_entry_id = cme_query_col(collection_with_children.id).body['collection-media-entry-arcs'][0]['media_entry_id']
          collection_id = cme_query_me(media_entry_id).body['collection-media-entry-arcs'][0]['collection_id']
          expect(@me[:id]).to be == media_entry_id
          expect(collection_id).to be == collection_with_children.id
    #    media_entry_resource = json_roa_keyword_resource.get.relation('collection-media-entry-arcs') \
    #      .get.collection.first.get.relation('media-entry').get
    #    collection_resource = media_entry_resource.relation('collection-media-entry-arcs') \
    #      .get.collection.first.get.relation('collection').get
    #    expect(@me[:id]).to be== media_entry_resource.data[:id]
    #    expect(collection_with_children[:id]).to be== collection_resource.data[:id]
      end

    end

  end

  context 'no children' do
    let :collection_without_children do
      FactoryBot.create(:collection,
                         get_metadata_and_previews: true)
    end

    let :json_roa_keyword_resource do
      JSON_ROA::Client.connect \
        "#{api_base_url}/collections/#{collection_without_children.id}"
    end

    # TODO json roa remove: test links: collection children relation media-entries
    #it 'responds with 200 for media-entries' do
    #  relation = json_roa_keyword_resource.get.relation('collections').get
    #  expect(relation.response.status).to be == 200
    #  expect(relation.data['collections'].length).to be == 0
    #end

    # TODO json roa remove: test links: collection children relation collections
    #it 'responds with 200 for collections' do
    #  relation = json_roa_keyword_resource.get.relation('media-entries').get
    #  expect(relation.response.status).to be == 200
    #  expect(relation.data['media-entries'].length).to be == 0
    #end

  end
end
