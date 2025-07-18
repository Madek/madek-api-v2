require "spec_helper"
require Pathname(File.expand_path("..", __FILE__)).join("shared")

context "A media-entry resource with get_metadata_and_previews permission" do
  before :each do
    @media_entry = FactoryBot.create :media_entry,
      get_metadata_and_previews: true
  end

  context "a meta datum of type text" do
    before :each do
      @meta_datum_text = FactoryBot.create :meta_datum_text,
        media_entry: @media_entry
    end

    describe "preconditions" do
      it "exists" do
        expect(MetaDatum.find(@meta_datum_text.id)).to be
      end

      it "belongs to the media-entry" do
        expect(@media_entry.meta_data).to include @meta_datum_text
      end
    end

    # TODO json roa: test links
    describe "resource" do
      include_context :media_entry_resource_via_plain_json

      describe "get meta-data relation" do
        let :meta_data_response do
          plain_faraday_json_client.get("/api-v2/media-entries/#{@media_entry.id}/meta-data/")
        end

        describe "meta_data the resource" do
          describe "the response" do
            it "the status code indicates success" do
              expect(meta_data_response.status).to be == 200
            end
          end
        end
      end

      # TODO test relations
      describe "get meta-data relation with query parameters" do
        describe "set meta_keys to some string" do
          # let :get_meta_data_relation do
          #  resource.relation('meta-data').get("meta_keys" => "bogus")
          # end
          # describe 'the response' do
          #  it '422s' do
          #    expect(get_meta_data_relation.response.status).to be == 422
          #  end
          # end
        end
        describe "set meta_keys to an json encoded array including the used key" do
          # let :get_meta_data_relation do
          #  resource.relation('meta-data') \
          #    .get("meta_keys" => [@meta_datum_text.meta_key_id].to_json)
          # end
          let :meta_data_response do
            plain_faraday_json_client.get("/api-v2/media-entries/#{CGI.escape(@media_entry.id)}/meta-data/")
          end

          let :get_meta_key_response do
            plain_faraday_json_client.get("/api-v2/meta-keys/#{@meta_datum_text.meta_key_id}")
          end

          describe "the response" do
            it "succeeds" do
              expect(get_meta_key_response.status).to be == 200
            end
            # it 'contains the meta-datum ' do
            #  expect(get_meta_data_relation.data['meta-data'].map{|x| x[:id]}).to \
            #    include @meta_datum_text.id
            # end
            it "contains the meta-datum " do
              # expect(meta_data_response.body['meta-data'].map{|x| x[:id]}).to \
              expect(meta_data_response.body["meta_data"][0]["id"]).to \
                include @meta_datum_text.id
            end
          end
        end
      end
    end
  end

  context "A media-entry resource without get_metadata_and_previews permission" do
    let :media_entry do
      FactoryBot.create :media_entry,
        get_metadata_and_previews: false
    end

    context "a meta datum of type text" do
      before :each do
        @meta_datum_text = FactoryBot.create :meta_datum_text,
          media_entry: media_entry
      end

      describe "preconditions" do
        it "exists" do
          expect(MetaDatum.find(@meta_datum_text.id)).to be
        end

        it "belongs to the media-entry" do
          expect(media_entry.meta_data).to include @meta_datum_text
        end
      end

      describe "resource" do
        include_context :media_entry_resource_via_plain_json
        # include_context :media_entry_resource_via_json_roa
        it "401s" do
          expect(response.status).to be == 401
        end
      end
    end
  end
end
