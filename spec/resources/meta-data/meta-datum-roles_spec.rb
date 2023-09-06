require 'spec_helper'
require Pathname(File.expand_path('..', __FILE__)).join('shared')

ROUNDS = 3.freeze

describe 'generated runs' do
  (1..ROUNDS).each do |round|
  #(1..1).each do |round|
    describe "ROUND #{round}" do
      describe 'meta_datum_roles_for_random_resource_type' do
        include_context :meta_datum_for_media_entry
        let(:meta_datum_roles) do
          FactoryBot.create 'meta_datum_roles',
                              media_entry: media_resource
        end

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
              #    .get('id' => meta_datum_roles.id)
              #end

              let :response do
                #resource.response
                authenticated_json_client.get("/api/meta-data/#{meta_datum_roles.id}")
              end

              it 'status, either 200 success or 403 forbidden, '\
                  'corresponds to the get_metadata_and_previews value' do
                expect(response.status).to be == \
                  (media_resource.get_metadata_and_previews ? 200 : 403)
              end

              context 'if the response is 200' do
                # TODO: remove this block
                # before :each do
                #   media_resource.update! \
                #     get_metadata_and_previews: true
                # end
                let(:value) { response.body['value'] }

                it 'it holds the proper uuid array value' do
                  if response.status == 200
                    value.map { |v| v['id'] }.each do |mtr_id|
                      mdi = response.body['id']
                      expect(MetaDatum::Role.find_by(meta_datum_id: mdi,
                                                       id: mtr_id))
                        .to be
                    end
                  end
                end

                context 'MetaDatum::Role resource' do
                  let(:root) { authenticated_json_roa_client.get }

                  it 'provides valid relations' do
                    if response.status == 200
                      response.body['value'].each do |v|
                        #meta_data_role = root.relation('meta-datum-role').get('id' => v['id'])
                        meta_data_role = authenticated_json_client.get("/api/meta-data-roles/#{v['id']}")
                        
                        meta_datum_id = meta_data_role.body['meta_datum_id']
                        expect(authenticated_json_client.get("/api/meta-data/#{meta_datum_id}").status)
                        #expect(meta_data_role.relation('meta-datum').get.response.status)
                          .to be == 200

                        person_id = meta_data_role.body['person_id']
                        expect(authenticated_json_client.get("/api/people/#{person_id}").status)
                        #expect(meta_data_role.relation('person').get.response.status)
                          .to be == 200
                        
                        unless meta_data_role.body['role_id'].nil?
                          role_id = meta_data_role.body['role_id']
                          expect(authenticated_json_client.get("/api/meta-data-roles/#{role_id}").status)
                        #  expect(meta_data_role.relation('role').get.response.status)
                            .to be == 200
                        end
                      end
                    end
                  end

                  context 'role is assigned' do
                    it 'has role relation' do
                      if response.status == 200
                        response.body['value'].each do |v|
                          #meta_data_role = root.relation('meta-datum-role').get('id' => v['id'])
                          meta_data_role = authenticated_json_client.get("/api/meta-data-roles/#{v['id']}")

                          #unless meta_data_role.data['role_id'].nil?
                          #  expect(meta_data_role.json_roa_data['relations']).to have_key 'role'
                          #end
                        end
                      end
                    end
                  end

                  context 'role is not assigned' do
                    it 'has no role relation' do
                      if response.status == 200
                        response.body['value'].each do |v|
                          #meta_data_role = root.relation('meta-datum-role').get('id' => v['id'])
                          meta_data_role = authenticated_json_client.get("/api/meta-data-roles/#{v['id']}")

                          #if meta_data_role.data['role_id'].nil?
                          #  expect(meta_data_role.json_roa_data['relations']).not_to have_key 'role'
                          #end
                        end
                      end
                    end
                  end
                end

                it 'it provides valid collection and relations' do
                  if response.status == 200
                    # TODO json roa remove: test collection: meta-data-roles
                    #resource.collection.each do |c_entry|
                    #  expect(c_entry.get.response.status).to be == 200
                    #  expect(value.map { |v| v['id'] }).to include c_entry.get.data['id']
                    #end

                    meta_key_id = response.body['meta_key_id']
                    
                    # TODO json roa remove: test links: meta-data-roles 2 meta-key
                    expect(authenticated_json_client.get("/api/meta-keys/#{meta_key_id}").status)
                    #expect(resource.relation('meta-key').get.response.status)
                      .to be == 200
                    # TODO json roa remove: test links: meta-data-roles 2 media-entry
                    media_entry_id =  response.body['media_entry_id']
                    expect(authenticated_json_client.get("/api/media-entries/#{media_entry_id}").status)
                    #expect(resource.relation('media-entry').get.response.status)
                      .to be == 200
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
