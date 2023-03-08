require 'spec_helper'
require Pathname(File.expand_path('..', __FILE__)).join('shared')

context 'A collection resource with get_metadata_and_previews permission' do
  before :each do
    @collection = FactoryBot.create :collection,
      get_metadata_and_previews: true
  end

  context 'a meta datum of type text' do
    before :each do
      @meta_datum_text = FactoryBot.create :meta_datum_text,
        collection: @collection
    end

    describe 'preconditions' do
      it 'exists' do
        expect(MetaDatum.find @meta_datum_text.id).to be
      end

      it 'belongs to the collection' do
        expect(@collection.meta_data).to include @meta_datum_text
      end
    end

    describe 'resource' do
      include_context :collection_resource_via_json
      # TODO json roa remove: test links: collections relation meta data 
      #it 'has a meta-data relation' do
      #  expect(resource.relation('meta-data')).to \
      #    be_a JSON_ROA::Client::Relation
      #end

      # TODO json roa remove: test links: collections relation meta data 
      describe 'get meta-data relation' do
      #  let :get_meta_data_relation do
      #    resource.relation('meta-data').get
      #  end
      let :meta_data_response do
        plain_faraday_json_client.get("/api/collection/#{CGI.escape(@collection.id)}/meta-data")
      end

      #  it 'is a resource' do
      #    expect(get_meta_data_relation).to be_a JSON_ROA::Client::Resource
      #  end

        describe 'meta_data the resource' do
      #    let :meta_data_resource do
      #      get_meta_data_relation
      #    end

          describe 'the response' do
            it 'the status code indicates success' do
              expect(meta_data_response.status).to be == 200
            end
          end
        end
      end

      describe 'get meta-data relation with query parameters' do
        describe 'set meta_keys to some string' do
      #    let :get_meta_data_relation do
      #      resource.relation('meta-data').get("meta_keys" => "bogus")
      #    end
          
          let :get_meta_key do
            plain_faraday_json_client.get("/api/meta-keys/bogus")
          end


          describe 'the response' do
            it '422s' do
              expect(get_meta_key.status).to be == 422
            end
          end
        end
        describe 'set meta_keys to an json encoded array including the used key' do
      #    let :get_meta_data_relation do
      #      resource.relation('meta-data') \
      #        .get("meta_keys" => [@meta_datum_text.meta_key_id].to_json)
      #    end
          # TODO query by array
          let :meta_data_response do
            plain_faraday_json_client.get("/api/collection/#{CGI.escape(@collection.id)}/meta-data")
          end
          
          let :get_meta_key_response do
            plain_faraday_json_client.get("/api/meta-keys/#{CGI::escape(@meta_datum_text.meta_key_id)}")
          end

          describe 'the response' do
            it 'succeeds' do
              expect(get_meta_key_response.status).to be == 200
            end
            it 'contains the meta-datum ' do
              #expect(meta_data_response.body['meta-data'].map{|x| x[:id]}).to \
              expect(meta_data_response.body['meta-data'][0]['id']).to \
                include @meta_datum_text.id
            end
          end
        end

      #  describe 'set meta_keys to an json encoded array excluding the used key' do
      #    let :get_meta_data_relation do
      #      resource.relation('meta-data') \
      #        .get("meta_keys" => ['bogus'].to_json)
      #    end
      #    describe 'the response' do
      #      it 'succeeds' do
      #        expect(get_meta_data_relation.response.status).to be == 200
      #      end
      #      it 'does not contain the meta-datum ' do
      #        expect(get_meta_data_relation.data['meta-data'].map{|x| x[:id]}).not_to \
      #          include @meta_datum_text.id
      #      end
      #    end
        end


      #end
    end
  end

  context 'A collection resource without get_metadata_and_previews permission' do
    before :each do
      @collection = FactoryBot.create :collection,
        get_metadata_and_previews: false
    end

    context 'a meta datum of type text' do
      before :each do
        @meta_datum_text = FactoryBot.create :meta_datum_text,
          collection: @collection
      end

      describe 'preconditions' do
        it 'exists' do
          expect(MetaDatum.find @meta_datum_text.id).to be
        end

        it 'belongs to the collection' do
          expect(@collection.meta_data).to include @meta_datum_text
        end
      end

      describe 'resource' do
        include_context :collection_resource_via_json
        it '401s' do
          expect(response.status).to be== 401
        end

      end

    end
  end
end

