require "spec_helper"
require Pathname(File.expand_path("..", __FILE__)).join("shared")

describe "generated runs" do
  include_context :json_client_for_authenticated_token_user do
    (1..ROUNDS).each do |round|
      # (1..1).each do |round|
      describe "ROUND #{round}" do
        describe "meta_datum_text_for_random_resource_type" do
          include_context :meta_datum_for_random_resource_type
          let(:meta_datum_text) { meta_datum ["text", "text_date"].sample }

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
                  client.get("/api-v2/meta-data/#{meta_datum_text.id}")
                end

                it "status, either 200 success or 403 forbidden, " \
                    "corresponds to the get_metadata_and_previews value" do
                  expect(response.status).to be ==
                    (media_resource.get_metadata_and_previews ? 200 : 403)
                end

                it "holds the proper text value when the response is 200" do
                  if response.status == 200
                    expect(response.body["value"]).to be == meta_datum_text.string
                  end
                end
              end

              describe "the meta-datum-data-stream resource" do
                let :response do
                  client.get("/api-v2/meta-data/#{meta_datum_text.id}/data-stream/")
                end

                it "status, either 200 success or 403 forbidden, " \
                    "corresponds to the get_metadata_and_previews value" do
                  expect(response.status).to be ==
                    (media_resource.get_metadata_and_previews ? 200 : 403)
                end

                it "holds the proper json value when the response is 200" do
                  if response.status == 200
                    expect(response.body).to be == meta_datum_text.value
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
