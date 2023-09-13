require 'spec_helper'
require 'json'
require Pathname(File.expand_path('../..', __FILE__)).join('shared')

describe 'generated runs' do
  #(1..ROUNDS).each do |round|
  (1..1).each do |round|
    describe "ROUND #{round}" do
      describe 'edit meta-data-keywords for random_resource_type' do
        include_context :random_resource_type
        let :meta_key do
          FactoryBot.create "meta_key_keywords"
        end
        let :keyword_data do
          FactoryBot.create :keyword
        end

        let :keyword_data2 do
          FactoryBot.create :keyword
        end

        #let (:md_url) { resource_url( meta_key.id) }
        let (:mdtype_url) { resource_url_typed( meta_key.id, "keyword" ) }
        let (:mdkw_url) { resource_url_typed_ided( meta_key.id, "keyword", keyword_data.id ) }
        let (:mdkw2_url) { resource_url_typed_ided( meta_key.id, "keyword", keyword_data2.id ) }
        
        
        describe 'authenticated_json_client' do
          include_context :authenticated_json_client

          after :each do |example|
            if example.exception
              example.exception.message << \
                "\n  MediaResource: #{media_resource} " \
                " #{media_resource.attributes}"
              example.exception.message << "\n  Client: #{client_entity} " \
                " #{client_entity.attributes}"

              example.exception.message << "\n  URL: #{mdkw_url} " \
                " #{client_entity.attributes}"
            end
          end

          describe 'with creator is authed user' do
            before :each do
              media_resource.update! \
                creator_id: client_entity.id,
                responsible_user_id: client_entity.id
            end

            describe 'create the meta-datum resource' do
              let :response do
                authenticated_json_client.post(mdkw_url)
                authenticated_json_client.post(mdkw2_url)
              end

              it 'status 200' do
                expect(response.status).to be == 200
              end

              it 'holds the proper meta-data value' do
                md = response.body['meta_data']
                test_resource_id(md)
              end

              it 'holds the new keywords value' do
                kwid = response.body['md_keywords'][0]['keyword_id']
                expect(kwid).to be == keyword_data.id
                kw2id = response.body['md_keywords'][1]['keyword_id']
                expect(kw2id).to be == keyword_data2.id
              end
            end

            describe 'read the meta-datum resource' do
              let :response do
                authenticated_json_client.post(mdkw_url)
                authenticated_json_client.post(mdkw2_url)

                authenticated_json_client.get(mdtype_url)
              end

              it 'status 200' do
                expect(response.status).to be == 200
              end

              it 'holds the proper meta-data' do
                md = response.body['meta_data']
                test_resource_id(md)
              end

              it 'holds the new keywords value' do
                kwid = response.body['md_keywords'][0]['keyword_id']
                kw2id = response.body['md_keywords'][1]['keyword_id']
                expect(kwid).to be == keyword_data.id
                expect(kw2id).to be == keyword_data2.id
              end

            end

            describe 'delete the meta-datum resource' do
              let :response do
                authenticated_json_client.post(mdkw_url)
                authenticated_json_client.post(mdkw2_url)

                authenticated_json_client.delete(mdkw_url)
                authenticated_json_client.get(mdtype_url)
              end

              it 'status 200' do
                expect(response.status).to be == 200
              end

              it 'holds the proper meta-data' do
                md = response.body['meta_data']
                test_resource_id(md)
              end

              it 'holds only undeleted keywords' do
                ckwid = response.body['md_keywords'][0]['keyword_id']
                expect(ckwid).to be == keyword_data2.id
              end

            end

          end
        end
      end
    end
  end
end
