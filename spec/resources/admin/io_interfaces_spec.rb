require "spec_helper"

context "admin io_interfaces" do
  before :each do
    @io_interface = FactoryBot.create :io_interface
    @create_data = {
      id: "someid",
      description: "sometext"
    }
  end

  let :query_url do
    "/api/admin/io_interfaces/"
  end

  let :io_interface_url do
    "/api/admin/io_interfaces/#{@io_interface.id}"
  end

  context "Responds not authorized without authentication" do
    describe "not authorized" do
      it "query responds with 403" do
        expect(plain_faraday_json_client.get(query_url).status).to be == 403
      end
      it "get responds with 403" do
        expect(plain_faraday_json_client.get(io_interface_url).status).to be == 403
      end
      it "post responds with 403" do
        resonse = plain_faraday_json_client.post(query_url) do |req|
          req.body = @create_data.to_json
          req.headers["Content-Type"] = "application/json"
        end
        expect(resonse.status).to be == 403
      end
      it "put responds with 403" do
        resonse = plain_faraday_json_client.put(io_interface_url) do |req|
          req.body = {}.to_json
          req.headers["Content-Type"] = "application/json"
        end
        expect(resonse.status).to be == 403
      end

      it "delete responds with 403" do
        expect(plain_faraday_json_client.delete(io_interface_url).status).to be == 403
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
          expect(client.get(io_interface_url).status).to be == 403
        end
        it "post responds with 403" do
          response = client.post(query_url) do |req|
            req.body = @create_data.to_json
            req.headers["Content-Type"] = "application/json"
          end
          expect(response.status).to be == 403
        end
        it "put responds with 403" do
          response = client.put(io_interface_url) do |req|
            req.body = {}.to_json
            req.headers["Content-Type"] = "application/json"
          end
          expect(response.status).to be == 403
        end
        it "delete responds with 403" do
          expect(client.delete(io_interface_url).status).to be == 403
        end
      end
    end
  end

  context "Responds ok as admin" do
    include_context :json_client_for_authenticated_admin_user do
      context "get" do
        it "responds 404 with non-existing id" do
          badid = Faker::Internet.uuid
          response = client.get("/api/admin/io_interfaces/#{badid}")
          expect(response.status).to be == 404
        end

        describe "existing id" do
          let :response do
            client.get(io_interface_url)
          end

          it "responds with 200" do
            expect(response.status).to be == 200
          end

          it "has the proper data" do
            data = response.body
            expect(
              data.except("created_at", "updated_at")
            ).to eq(
              @io_interface.attributes.with_indifferent_access
                .except(:created_at, :updated_at)
            )
          end
        end
      end

      context "post" do
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

      context "put" do
        let :response do
          client.put(io_interface_url) do |req|
            req.body = {
              description: "other text"
            }.to_json
            req.headers["Content-Type"] = "application/json"
          end
        end

        it "responds with 200" do
          expect(response.status).to be == 200
        end

        it "has the proper data" do
          data = response.body
          expect(data["description"]).to be == "other text"
        end
      end

      context "delete" do
        let :response do
          client.delete(io_interface_url)
        end

        it "responds with 200" do
          expect(response.status).to be == 200
        end

        it "has the proper data" do
          data = response.body
          expect(
            data.except("created_at", "updated_at")
          ).to eq(
            @io_interface.attributes.with_indifferent_access
              .except(:created_at, :updated_at)
          )
        end
      end
    end
  end
end
