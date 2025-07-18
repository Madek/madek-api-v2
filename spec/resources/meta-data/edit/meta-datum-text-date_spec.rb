require "spec_helper"
require "json"
require Pathname(File.expand_path("../..", __FILE__)).join("shared")

describe "generated runs" do
  include_context :json_client_for_authenticated_token_user do
    # (1..ROUNDS).each do |round|
    (1..1).each do |round|
      describe "ROUND #{round}" do
        describe "edit meta-data-text for random_resource_type" do
          include_context :random_resource_type
          let :meta_key do
            FactoryBot.create "meta_key_text_date"
          end
          let(:post_url) { resource_url_typed(meta_key.id, "text-dates") }
          let(:delete_url) { resource_url(meta_key.id) }

          let(:create_data) { "Hello Lala" }
          let(:update_data) { "Bye Bye Ugale" }

          describe "client" do
            after :each do |example|
              if example.exception
                example.exception.message <<
                  "\n  MediaResource: #{media_resource} " \
                    " #{media_resource.attributes}"
                example.exception.message << "\n  Client: #{entity} " \
                  " #{entity.attributes}"

                example.exception.message << "\n  URL: #{post_url} " \
                  " #{entity.attributes}"
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
                  client.post(post_url) do |req|
                    req.body = {string: create_data}.to_json
                    req.headers["Content-Type"] = "application/json"
                  end
                end

                it "status 200" do
                  expect(response.status).to be == 200
                end

                it "holds the proper value" do
                  expect(response.body["string"]).to eq(create_data)
                end
              end

              describe "read the meta-datum resource" do
                let :response do
                  client.post(post_url) do |req|
                    req.body = {string: create_data}.to_json
                    req.headers["Content-Type"] = "application/json"
                  end

                  client.get(delete_url)
                end

                it "status 200" do
                  expect(response.status).to be == 200
                end

                it "holds the proper value" do
                  expect(response.body["meta_data"]["string"]).to eq(create_data)
                end
              end

              describe "update the meta-datum resource" do
                let :response do
                  client.post(post_url) do |req|
                    req.body = {string: create_data}.to_json
                    req.headers["Content-Type"] = "application/json"
                  end

                  client.put(post_url) do |req|
                    req.body = {string: update_data}.to_json
                    req.headers["Content-Type"] = "application/json"
                  end
                end

                it "status 200" do
                  expect(response.status).to be == 200
                end

                it "holds the proper value" do
                  expect(response.body["string"]).to eq(update_data)
                end
              end

              describe "delete the meta-datum resource" do
                let :response do
                  client.post(post_url) do |req|
                    req.body = {string: create_data}.to_json
                    req.headers["Content-Type"] = "application/json"
                  end

                  client.delete(delete_url)
                end

                it "status 200" do
                  expect(response.status).to be == 200
                end

                it "holds the proper value" do
                  expect(response.body["string"]).to eq(create_data)
                end
              end
            end
          end
        end
      end
    end
  end
end
