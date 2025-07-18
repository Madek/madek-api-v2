require "spec_helper"
require "shared/audit-validator"

context "admin context-keys/" do
  before :each do
    @context_key = FactoryBot.create :context_key
    # TODO use Faker and indiv. data
    @labels = {de: "labelde", en: "labelen"}
    @context = create(:context)
    @meta_key = create(:meta_key_text)
    @create_data = {
      admin_comment: "nocomment",
      context_id: @context.id,
      meta_key_id: @meta_key.id,
      is_required: true,
      position: 1,
      length_min: 0,
      length_max: 128,
      labels: @labels,
      hints: @labels,
      descriptions: @labels,
      documentation_urls: @labels
    }

    @invalid_create_data = {
      context_id: "invalid",
      meta_key_id: "invalid",
      is_required: true,
      position: 1
    }
  end

  let :query_url do
    "/api-v2/admin/context-keys/"
  end

  let :context_key_url do
    "/api-v2/admin/context-keys/#{@context_key.id}"
  end

  context "Responds not authorized without authentication" do
    describe "not authorized" do
      it "query responds with 403" do
        expect(plain_faraday_json_client.get(query_url).status).to be == 403
      end
      it "get responds with 403" do
        expect(plain_faraday_json_client.get(context_key_url).status).to be == 403
      end
      it "post responds with 403" do
        resonse = plain_faraday_json_client.post(query_url) do |req|
          req.body = @invalid_create_data.to_json
          req.headers["Content-Type"] = "application/json"
        end
        expect(resonse.status).to be == 403
      end
      it "put responds with 403" do
        resonse = plain_faraday_json_client.put(context_key_url) do |req|
          req.body = {}.to_json
          req.headers["Content-Type"] = "application/json"
        end
        expect(resonse.status).to be == 403
      end

      it "delete responds with 403" do
        expect(plain_faraday_json_client.delete(context_key_url).status).to be == 403
      end
    end
  end

  context "Responds not authorized as user" do
    include_context :json_client_for_authenticated_token_user do
      before :each do
        @context_key = FactoryBot.create :context_key
      end

      describe "not authorized" do
        it "query responds with 403" do
          expect(client.get(query_url).status).to be == 403
        end
        it "get responds with 403" do
          expect(client.get(context_key_url).status).to be == 403
        end
        it "post responds with 403" do
          response = client.post(query_url) do |req|
            req.body = @invalid_create_data.to_json
            req.headers["Content-Type"] = "application/json"
          end
          expect(response.status).to be == 403
        end
        it "put responds with 403" do
          response = client.put(context_key_url) do |req|
            req.body = {}.to_json
            req.headers["Content-Type"] = "application/json"
          end
          expect(response.status).to be == 403
        end
        it "delete responds with 403" do
          expect(client.delete(context_key_url).status).to be == 403
        end
      end
    end
  end

  context "Responds ok as admin" do
    include_context :json_client_for_authenticated_token_admin do
      context "get" do
        it "responds 404 with non-existing id" do
          badid = Faker::Internet.uuid
          response = client.get("/api-v2/admin/context-keys/#{badid}")
          expect(response.status).to be == 404
        end

        describe "existing id" do
          let :response do
            client.get(context_key_url)
          end

          it "responds with 200" do
            expect(response.status).to be == 200
          end

          it "has the proper data" do
            data = response.body
            expect(
              data.except("created_at", "updated_at")
            ).to eq(
              @context_key.attributes.with_indifferent_access
                .except(:created_at, :updated_at)
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
            data.except("id", "created_at", "updated_at")
          ).to eq(
            @create_data.with_indifferent_access
              .except(:id, :created_at, :updated_at)
          )
        end
      end

      # TODO test more data
      context "put" do
        let :response do
          client.put(context_key_url) do |req|
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
            @context_key.attributes.with_indifferent_access
              .except(:created_at, :updated_at,
                :position)
          )
          expect(data["position"]).to be == 1
        end
      end

      context "delete" do
        let :response do
          client.delete(context_key_url)
        end

        it "responds with 200" do
          expect(response.status).to be == 200
        end

        it "has the proper data" do
          data = response.body
          expect(
            data.except("created_at", "updated_at")
          ).to eq(
            @context_key.attributes.with_indifferent_access
              .except(:created_at, :updated_at)
          )
        end
      end
    end
  end
end

context "Getting context-keys with pagination" do
  include_context :json_client_for_authenticated_token_admin do
    before :each do
      @keywords = []
      10.times do
        @keywords << FactoryBot.create(:context_key)
      end
    end

    it "responses with 200" do
      resp1 = client.get("/api-v2/admin/context-keys/?page=1&size=5")
      expect(resp1.status).to be == 200
      expect(resp1.body["data"].count).to be 5

      resp2 = client.get("/api-v2/admin/context-keys/?page=2&size=5")
      expect(resp2.status).to be == 200
      expect(resp2.body["data"].count).to be 5
      expect(resp2.body["pagination"]).to be_a Hash

      expect(lists_of_maps_different?(resp1.body["data"], resp2.body["data"])).to eq true
    end

    it "responses with 200" do
      resp = client.get("/api-v2/admin/context-keys/")
      expect(resp.status).to be == 200
      expect(resp.body.count).to be 10
      expect(resp.body).to be_a Array
    end
  end
end
