require "spec_helper"
require "json"
require Pathname(File.expand_path("../..", __FILE__)).join("shared")

describe "generated runs" do
  # (1..ROUNDS).each do |round|
  (1..1).each do |round|
    describe "ROUND #{round}" do
      describe "edit meta-data-json for random_resource_type" do
        include_context :random_resource_type
        let :meta_key do
          FactoryBot.create "meta_key_json"
        end
        let(:post_url) { resource_url_typed(meta_key.id, "json") }
        let(:delete_url) { resource_url(meta_key.id) }

        let(:create_data) { {some_boolean: true, zero_point: -273.15, seq: [1, 2, nil]} }
        let(:update_data) { {some_boolean2: true, zero_point: -273.15, seq2: [1, 2, nil]} }

        # let (:put_url) { resource_url_put "json" }

        # let(:meta_datum_json) { meta_datum "json" }

        describe "authenticated_json_client" do
          include_context :authenticated_json_client

          after :each do |example|
            if example.exception
              example.exception.message << \
                "\n  MediaResource: #{media_resource} " \
                " #{media_resource.attributes}"
              example.exception.message << "\n  Client: #{client_entity} " \
                " #{client_entity.attributes}"

              example.exception.message << "\n  URL: #{post_url} " \
                " #{delete_url}"
              example.exception.message << "\n  Meta-Key #{meta_key} " \
              " #{meta_key.attributes}"

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
                authenticated_json_client.post(post_url) do |req|
                  req.body = {json: JSON.dump(create_data)}.to_json
                  req.headers["Content-Type"] = "application/json"
                end
              end

              it "status 200" do
                expect(response.status).to be == 200
              end

              let :json_value do
                response.body["json"]
              end

              it "holds the proper json value" do
                expect(json_value).to eq(create_data.with_indifferent_access)
              end
            end

            describe "read the meta-datum resource" do
              let :response do
                authenticated_json_client.post(post_url) do |req|
                  req.body = {json: JSON.dump(create_data)}.to_json
                  req.headers["Content-Type"] = "application/json"
                end

                authenticated_json_client.get(delete_url)
              end

              it "status 200" do
                expect(response.status).to be == 200
              end

              let :json_value do
                response.body["meta-data"]["json"]
              end

              it "holds the proper json value" do
                expect(json_value).to eq(create_data.with_indifferent_access)
              end
            end

            describe "update the meta-datum resource" do
              let :response do
                authenticated_json_client.post(post_url) do |req|
                  req.body = {json: JSON.dump(create_data)}.to_json
                  req.headers["Content-Type"] = "application/json"
                end

                authenticated_json_client.put(post_url) do |req|
                  req.body = {json: JSON.dump(update_data)}.to_json
                  req.headers["Content-Type"] = "application/json"
                end
              end

              it "status 200" do
                expect(response.status).to be == 200
              end

              let :json_value do
                response.body["json"]
              end

              it "holds the proper json value" do
                expect(json_value).to eq(update_data.with_indifferent_access)
              end
            end

            describe "delete the meta-datum resource" do
              let :response do
                authenticated_json_client.post(post_url) do |req|
                  req.body = {json: JSON.dump(create_data)}.to_json
                  req.headers["Content-Type"] = "application/json"
                end

                authenticated_json_client.delete(delete_url)
              end

              it "status 200" do
                expect(response.status).to be == 200
              end

              let :json_value do
                response.body["json"]
              end

              it "holds the proper json value" do
                expect(json_value).to eq(create_data.with_indifferent_access)
              end
            end
          end
        end
      end
    end
  end
end
