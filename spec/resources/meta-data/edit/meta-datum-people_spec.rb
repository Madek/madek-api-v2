require "spec_helper"
require "json"
require Pathname(File.expand_path("../..", __FILE__)).join("shared")

describe "generated runs" do
  # (1..ROUNDS).each do |round|
  (1..1).each do |round|
    describe "ROUND #{round}" do
      describe "edit meta-data-people for random_resource_type" do
        include_context :random_resource_type
        let :meta_key do
          FactoryBot.create "meta_key_people"
        end
        let :person_data do
          FactoryBot.create :person
        end

        let :person_data2 do
          FactoryBot.create :person
        end

        let :people_data_ids do
          [person_data.id, person_data2.id]
        end

        let(:mdtype_url) { resource_url_typed(meta_key.id, "people") }
        let(:mdp_url) { resource_url_typed_ided(meta_key.id, "people", person_data.id) }
        let(:mdp2_url) { resource_url_typed_ided(meta_key.id, "people", person_data2.id) }

        describe "authenticated_json_client" do
          include_context :authenticated_json_client

          after :each do |example|
            if example.exception
              example.exception.message << \
                "\n  MediaResource: #{media_resource} " \
                " #{media_resource.attributes}"
              example.exception.message << "\n  Client: #{client_entity} " \
                " #{client_entity.attributes}"

              example.exception.message << "\n  URLs: #{mdtype_url} : " \
                " #{mdp_url}"
            end
          end

          describe "with creator is authed user" do
            before :each do
              media_resource.update! \
                creator_id: client_entity.id,
                responsible_user_id: client_entity.id
            end

            describe "create the meta-datum resource" do
              let :response do
                expect(authenticated_json_client.post(mdp_url).status).to be == 200
                expect(authenticated_json_client.post(mdp2_url).status).to be == 200

                authenticated_json_client.get(mdtype_url)
              end

              it "status 200" do
                expect(response.status).to be == 200
              end

              it "holds the proper meta-data value" do
                md = response.body["meta_data"]
                test_resource_id(md)
              end

              it "holds the new meta-data-people value" do
                response.body["md_people"].each do |md_person|
                  expect(people_data_ids).to include md_person["person_id"]
                end
              end

              it "holds the new people_ids value" do
                response.body["people_ids"].each do |person_id|
                  expect(people_data_ids).to include person_id
                end
              end

              it "holds the new people value" do
                response.body["people"].each do |person|
                  expect(people_data_ids).to include person["id"]
                end
              end
            end

            describe "create and delete the meta-datum resource" do
              let :response do
                expect(authenticated_json_client.post(mdp_url).status).to be == 200
                expect(authenticated_json_client.post(mdp2_url).status).to be == 200

                expect(authenticated_json_client.delete(mdp2_url).status).to be == 200

                authenticated_json_client.get(mdtype_url)
              end

              it "status 200" do
                expect(response.status).to be == 200
              end

              it "holds the proper meta-data value" do
                md = response.body["meta_data"]
                test_resource_id(md)
              end

              it "holds the new meta-data-people value" do
                expect(response.body["md_people"][0]["person_id"]).to be == person_data.id
              end

              it "holds the new people_ids value" do
                expect(response.body["people_ids"][0]).to be == person_data.id
              end

              it "holds the new people value" do
                expect(response.body["people"][0]["id"]).to be == person_data.id
              end
            end
          end
        end
      end
    end
  end
end
