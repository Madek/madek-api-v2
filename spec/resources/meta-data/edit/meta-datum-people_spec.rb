require "spec_helper"
require "json"
require Pathname(File.expand_path("../..", __FILE__)).join("shared")

describe "generated runs" do
  include_context :json_client_for_authenticated_token_user do
    (1..ROUNDS).each do |round|
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

          let(:mdtype_url) { resource_url_typed(meta_key.id, "meta-data-people") }

          describe "client" do
            after :each do |example|
              if example.exception
                example.exception.message <<
                  "\n  MediaResource: #{media_resource} " \
                  " #{media_resource.attributes}"
                example.exception.message << "\n  Client: #{entity} " \
                  " #{entity.attributes}"

                example.exception.message << "\n  URLs: #{mdtype_url}"
              end
            end

            describe "with creator is authed user" do
              before :each do
                media_resource.update! \
                  creator_id: entity.id,
                  responsible_user_id: entity.id
              end

              describe "create the meta-datum resource" do
                let :response do
                  expect(
                    client.post(mdtype_url) do |req|
                      req.body = {person_id: person_data.id}.to_json
                      req.headers["Content-Type"] = "application/json"
                    end.status
                  ).to be == 200
                  expect(
                    client.post(mdtype_url) do |req|
                      req.body = {person_id: person_data2.id}.to_json
                      req.headers["Content-Type"] = "application/json"
                    end.status
                  ).to be == 200

                  client.get(mdtype_url)
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
              end

              describe "create and delete the meta-datum resource" do
                let :response do
                  expect(
                    client.post(mdtype_url) do |req|
                      req.body = {person_id: person_data.id}.to_json
                      req.headers["Content-Type"] = "application/json"
                    end.status
                  ).to be == 200

                  resp = client.post(mdtype_url) do |req|
                    req.body = {person_id: person_data2.id}.to_json
                    req.headers["Content-Type"] = "application/json"
                  end

                  expect(resp.status).to be == 200

                  mdp_id = resp.body["md_people"]["id"]
                  url = resource_url_typed_ided(meta_key.id, "meta-data-people", mdp_id)
                  expect(client.delete(url).status).to be == 200

                  client.get(mdtype_url)
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
              end
            end
          end
        end
      end
    end
  end
end
