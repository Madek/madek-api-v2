require "spec_helper"

context "admin keywords" do
  before :each do
    @vocabulary = FactoryBot.create :vocabulary
    @meta_key = FactoryBot.create :meta_key_text
    @keyword = FactoryBot.create :keyword
  end

  let :query_url do
    "/api/admin/keywords/"
  end

  let :keyword_url do
    "/api/admin/keywords/#{@keyword.id}"
  end

  context "Responds not authorized without authentication" do
    describe "not authorized" do
      it "query responds with 403" do
        expect(plain_faraday_json_client.get(query_url).status).to be == 403
      end
      it "get responds with 403" do
        expect(plain_faraday_json_client.get(keyword_url).status).to be == 403
      end
      it "post responds with 403" do
        resonse = plain_faraday_json_client.post(query_url) do |req|
          req.body = {
            term: "someterm",
            meta_key_id: @meta_key.id
          }.to_json
          req.headers["Content-Type"] = "application/json"
        end
        expect(resonse.status).to be == 403
      end
      it "put responds with 403" do
        resonse = plain_faraday_json_client.put(keyword_url) do |req|
          req.body = {}.to_json
          req.headers["Content-Type"] = "application/json"
        end
        expect(resonse.status).to be == 403
      end

      it "delete responds with 403" do
        expect(plain_faraday_json_client.delete(keyword_url).status).to be == 403
      end
    end
  end

  context "Responds not authorized as user" do
    include_context :json_client_for_authenticated_user do
      describe "not authorized" do
        it "query responds with 403" do
          expect(client.get(query_url).status).to be == 403
        end
        it "get responds with 403" do
          expect(client.get(keyword_url).status).to be == 403
        end
        it "post responds with 403" do
          response = client.post(query_url) do |req|
            req.body = {
              term: "invalid",
              meta_key_id: "invalid",
              position: 1
            }.to_json
            req.headers["Content-Type"] = "application/json"
          end
          expect(response.status).to be == 403
        end
        it "put responds with 403" do
          response = client.put(keyword_url) do |req|
            req.body = {}.to_json
            req.headers["Content-Type"] = "application/json"
          end
          expect(response.status).to be == 403
        end
        it "delete responds with 403" do
          expect(client.delete(keyword_url).status).to be == 403
        end
      end
    end
  end

  context "Responds ok as admin" do
    include_context :json_client_for_authenticated_admin_user do
      context "get" do
        it "responds 400 with bad formatted uuid" do
          badid = Faker::Internet.slug(words: nil, glue: "-")
          response = client.get("/api/admin/keywords/#{badid}")
          expect(response.status).to be == 400
        end

        it "responds 404 with non-existing id" do
          badid = Faker::Internet.uuid
          response = client.get("/api/admin/keywords/#{badid}")
          expect(response.status).to be == 404
        end

        describe "existing id" do
          let :response do
            client.get(keyword_url)
          end

          it "responds with 200" do
            expect(response.status).to be == 200
          end

          it "has the proper data" do
            data = response.body
            expect(
              data.except("created_at", "updated_at", "external_uri")
            ).to eq(
              @keyword.attributes.with_indifferent_access
                .except(:created_at, :updated_at, :external_uri)
            )
          end
        end
      end

      context "post" do
        before :each do
          # @meta_key = create(:meta_key_text)
          # TODO use Faker and indiv. data
          @create_data = {
            term: "someterm",
            description: "some desc",
            meta_key_id: @meta_key.id,
            position: 1,
            external_uris: ["hallo", "byebye"],
            rdf_class: "Keyword"
          }
        end

        let :response do
          client.post(query_url) do |req|
            req.body = @create_data.to_json
            req.headers["Content-Type"] = "application/json"
          end
        end

        it "responds with 200" do
          expect(response.status).to be == 200
        end

        it "has the proper data" do
          data = response.body

          expect(
            data.except("id", "created_at", "updated_at", "external_uri", "creator_id")
          ).to eq(
            @create_data.with_indifferent_access
              .except(:id, :created_at, :updated_at, :external_uri, :creator_id)
          )
        end
      end

      # TODO test more data
      context "put" do
        let :response do
          client.put(keyword_url) do |req|
            req.body = {
              position: 2
            }.to_json
            req.headers["Content-Type"] = "application/json"
          end
        end

        it "responds with 200" do
          expect(response.status).to be == 200
        end

        it "has the proper data" do
          data = response.body
          expect(
            data.except("created_at", "updated_at",
              "position", "external_uri")
          ).to eq(
            @keyword.attributes.with_indifferent_access
              .except(:created_at, :updated_at,
                :position, :external_uri)
          )
          expect(data["position"]).to be == 2
        end
      end

      context "delete" do
        let :response do
          client.delete(keyword_url)
        end

        it "responds with 200" do
          expect(response.status).to be == 200
        end

        it "has the proper data" do
          data = response.body
          expect(
            data.except("created_at", "updated_at", "external_uri")
          ).to eq(
            @keyword.attributes.with_indifferent_access
              .except(:created_at, :updated_at, :external_uri)
          )
        end
      end
    end
  end
end
