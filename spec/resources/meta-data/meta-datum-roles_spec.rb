require "spec_helper"
require Pathname(File.expand_path("..", __FILE__)).join("shared")

ROUNDS = 3

describe "generated runs" do
  include_context :json_client_for_authenticated_token_user do
    (1..ROUNDS).each do |round|
      # (1..1).each do |round|
      describe "ROUND #{round}" do
        describe "meta_datum_roles_for_random_resource_type" do
          include_context :meta_datum_for_media_entry
          let(:meta_datum_roles) do
            FactoryBot.create "meta_datum_roles",
              media_entry: media_resource
          end

          describe "client" do
            after :each do |example|
              if example.exception
                example.exception.message <<
                  "\n  MediaResource: #{media_resource} " \
                  " #{media_resource.attributes}"
                example.exception.message << "\n  Client: #{entity} " \
                  " #{entity.attributes}"
              end
            end
            describe "with random public view permission" do
              before :each do
                media_resource.update! \
                  get_metadata_and_previews: (rand <= 0.5)
              end
              describe "the meta-data resource" do
                let :response do
                  client.get("/api-v2/meta-data/#{meta_datum_roles.id}")
                end

                it "status, either 200 success or 403 forbidden, " \
                    "corresponds to the get_metadata_and_previews value" do
                  expect(response.status).to be ==
                    (media_resource.get_metadata_and_previews ? 200 : 403)
                end

                context "if the response is 200" do
                  # TODO: remove this block
                  # before :each do
                  #   media_resource.update! \
                  #     get_metadata_and_previews: true
                  # end
                  let(:value) { response.body["value"] }

                  it "it holds the proper uuid array value" do
                    if response.status == 200
                      value.map { |v| v["id"] }.each do |mtr_id|
                        mdi = response.body["id"]
                        expect(MetaDatum::Role.find_by(meta_datum_id: mdi,
                          id: mtr_id))
                          .to be
                      end
                    end
                  end

                  context "MetaDatum::Role resource" do
                    it "provides valid relations" do
                      if response.status == 200
                        response.body["value"].each do |v|
                          meta_data_role = client.get("/api-v2/meta-data-roles/#{v["id"]}")

                          expect(meta_data_role.status).to be == 200

                          unless meta_data_role.body["id"].nil?

                            meta_datum_id = meta_data_role.body["meta_datum_id"]

                            expect(client.get("/api-v2/meta-data/#{meta_datum_id}").status)
                              .to be == 200

                            person_id = meta_data_role.body["person_id"]
                            expect(client.get("/api-v2/people/#{person_id}").status)
                              .to be == 200

                            unless meta_data_role.body["role_id"].nil?
                              role_id = meta_data_role.body["role_id"]
                              expect(client.get("/api-v2/roles/#{role_id}").status)
                                .to be == 200
                            end
                          end
                        end
                      end
                    end

                    context "role is assigned" do
                      it "has role relation" do
                        if response.status == 200
                          response.body["value"].each do |v|
                            meta_data_role = client.get("/api-v2/meta-data-role/#{v["id"]}")

                            unless meta_data_role.body["role_id"].nil?
                              #  expect(meta_data_role.json_roa_data['relations']).to have_key 'role'
                              role_id = meta_data_role.body["role_id"]
                              expect(client.get("/api-v2/roles/#{role_id}").status)
                                .to be == 200
                            end
                          end
                        end
                      end
                    end
                  end

                  it "it provides valid collection and relations" do
                    if response.status == 200
                      # TODO json roa remove: test collection: meta-data-roles
                      collection_data = response.body["value"]
                      collection_data.each do |c_entry|
                        expect(value.map { |v| v["id"] }).to include c_entry["id"]
                      end

                      # resource.collection.each do |c_entry|
                      #  expect(c_entry.get.response.status).to be == 200
                      #  expect(value.map { |v| v['id'] }).to include c_entry.get.data['id']
                      # end

                      meta_key_id = response.body["meta_key_id"]
                      expect(client.get("/api-v2/meta-keys/#{meta_key_id}").status)
                        .to be == 200

                      if response.body["media_entry_id"] == media_resource.id
                        media_entry_id = response.body["media_entry_id"]
                        expect(client.get("/api-v2/media-entries/#{media_entry_id}").status)
                          .to be == 200
                      end
                      if response.body["collection_id"] == media_resource.id
                        collection_id = response.body["collection_id"]
                        expect(client.get("/api-v2/collections/#{collection_id}").status)
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
  end
end
