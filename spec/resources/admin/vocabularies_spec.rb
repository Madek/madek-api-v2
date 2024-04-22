require "spec_helper"

context "admin vocabularies" do
  before :each do
    @vocabulary = FactoryBot.create :vocabulary
    # TODO use Faker and indiv. data
    @labels = {de: "labelde", en: "labelen"}

    @create_data = {
      id: "myvocab",
      enabled_for_public_view: true,
      enabled_for_public_use: true,
      position: 1,
      labels: @labels,
      descriptions: @labels,
      admin_comment: "nocomment"
    }
  end

  let :query_url do
    "/api/admin/vocabularies/"
  end

  let :vocabulary_url do
    "/api/admin/vocabularies/#{@vocabulary.id}"
  end

  context "Responds not authorized without authentication" do
    describe "not authorized" do
      it "query responds with 403" do
        expect(plain_faraday_json_client.get(query_url).status).to be == 403
      end
      it "get responds with 403" do
        expect(plain_faraday_json_client.get(vocabulary_url).status).to be == 403
      end
      it "post responds with 403" do
        resonse = plain_faraday_json_client.post(query_url) do |req|
          req.body = @create_data.to_json
          req.headers["Content-Type"] = "application/json"
        end
        expect(resonse.status).to be == 403
      end
      it "put responds with 403" do
        resonse = plain_faraday_json_client.put(vocabulary_url) do |req|
          req.body = {}.to_json
          req.headers["Content-Type"] = "application/json"
        end
        expect(resonse.status).to be == 403
      end

      it "delete responds with 403" do
        expect(plain_faraday_json_client.delete(vocabulary_url).status).to be == 403
      end
    end
  end

  context "Responds not authorized as user" do
    include_context :json_client_for_authenticated_user do
      before :each do
        @vocabulary = FactoryBot.create :vocabulary
      end

      describe "not authorized" do
        it "query responds with 403" do
          expect(client.get(query_url).status).to be == 403
        end
        it "get responds with 403" do
          expect(client.get(vocabulary_url).status).to be == 403
        end
        it "post responds with 403" do
          response = client.post(query_url) do |req|
            req.body = @create_data.to_json
            req.headers["Content-Type"] = "application/json"
          end
          expect(response.status).to be == 403
        end
        it "put responds with 403" do
          response = client.put(vocabulary_url) do |req|
            req.body = {}.to_json
            req.headers["Content-Type"] = "application/json"
          end
          expect(response.status).to be == 403
        end
        it "delete responds with 403" do
          expect(client.delete(vocabulary_url).status).to be == 403
        end
      end
    end
  end

  context "Responds ok as admin" do
    include_context :json_client_for_authenticated_admin_user do
      context "get" do
        it "responds 404 with non-existing id" do
          badid = Faker::Internet.uuid
          response = client.get("/api/admin/vocabularies/#{badid}")
          expect(response.status).to be == 404
        end

        describe "existing id" do
          let :response do
            client.get(vocabulary_url)
          end

          it "responds with 200" do
            expect(response.status).to be == 200
          end

          it "has the proper data" do
            data = response.body
            expect(
              data.except("created_at", "updated_at", "admin_comment")
            ).to eq(
              @vocabulary.attributes.with_indifferent_access
                .except(:created_at, :updated_at, :admin_comment)
            )
          end
        end
      end

      context "post" do
        before :each do
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
            data.except("created_at", "updated_at")
          ).to eq(
            @create_data.with_indifferent_access
              .except(:created_at, :updated_at)
          )
        end
      end

      # TODO test more data
      context "put" do
        let :response do
          client.put(vocabulary_url) do |req|
            req.body = {
              position: 1
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
              "position")
          ).to eq(
            @vocabulary.attributes.with_indifferent_access
              .except(:created_at, :updated_at,
                :position)
          )
          expect(data["position"]).to be == 1
        end
      end

      context "delete" do
        let :response do
          client.delete(vocabulary_url)
        end

        it "responds with 200" do
          expect(response.status).to be == 200
        end

        it "has the proper data" do
          data = response.body
          expect(
            data.except("created_at", "updated_at")
          ).to eq(
            @vocabulary.attributes.with_indifferent_access
              .except(:created_at, :updated_at)
          )
        end
      end
    end
  end
end
