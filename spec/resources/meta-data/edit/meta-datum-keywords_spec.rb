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

        let :keywords_data_ids do
          [keyword_data.id, keyword_data2.id]
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
                response.body['md_keywords'].each do |md_keyword|
                  expect(keywords_data_ids).to include md_keyword['keyword_id']
                end
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

              it 'holds the new meta-data-keywords value' do
                response.body['md_keywords'].each do |md_keyword|
                  expect(keywords_data_ids).to include md_keyword['keyword_id']
                end
              end

              it 'holds the new keywords ids value' do
                response.body['keywords_ids'].each do |keyword_id|
                  expect(keywords_data_ids).to include keyword_id
                end
              end

              it 'holds the new keywords value' do
                response.body['keywords'].each do |keyword|
                  expect(keywords_data_ids).to include keyword['id']
                end
              end

            end

            describe 'delete the meta-datum resource partly' do

              let :get_response do
                expect(authenticated_json_client.post(mdkw_url).status).to be == 200
                expect(authenticated_json_client.post(mdkw2_url).status).to be == 200
                
                del_response = authenticated_json_client.delete(mdkw_url)
                expect(del_response.status).to be == 200
                expect(del_response.body['md_keywords'][0]['keyword_id']).to be == keyword_data2.id

                authenticated_json_client.get(mdtype_url)
              end


              it 'status 200' do
                expect(get_response.status).to be == 200
              end

              it 'holds the proper meta-data' do
                md = get_response.body['meta_data']
                test_resource_id(md)
              end

              it 'holds only undeleted keywords' do
                ckwid = get_response.body['md_keywords'][0]['keyword_id']
                expect(ckwid).to be == keyword_data2.id
              end
            end

            describe 'delete the meta-datum resource complete' do

              it 'deleted keywords' do
                expect(authenticated_json_client.post(mdkw_url).status).to be == 200
                expect(authenticated_json_client.post(mdkw2_url).status).to be == 200

                expect(authenticated_json_client.get(mdtype_url).status).to be == 200

                expect(authenticated_json_client.delete(mdkw_url).status).to be == 200
                expect(authenticated_json_client.get(mdtype_url).status).to be == 200
                expect(authenticated_json_client.delete(mdkw2_url).status).to be == 200

                expect(authenticated_json_client.get(mdtype_url).status).to be == 404
              end
            end

          end
        end
      end
    end
  end
end
