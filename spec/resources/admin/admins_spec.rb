require "spec_helper"

context "admin users" do
  before :each do
    @admin = FactoryBot.create :admin
    @user = FactoryBot.create :user
  end

  let :query_url do
    "/api/admin/admins/"
  end
  let :admin_url do
    "/api/admin/admins/#{@admin.id}"
  end
  let :admin_user_url do
    "/api/admin/admins/#{@admin.user_id}/user"
  end

  let :user_url do
    "/api/admin/admins/#{@user.id}/user"
  end

  context "Responds not authorized without authentication" do
    describe "not authorized" do
      it "query responds with 403" do
        expect(plain_faraday_json_client.get(query_url).status).to be == 403
      end
      it "get responds with 403" do
        expect(plain_faraday_json_client.get(admin_url).status).to be == 403
      end
      it "post responds with 403" do
        resonse = plain_faraday_json_client.post(user_url) do |req|
          req.body = {}.to_json
          req.headers["Content-Type"] = "application/json"
        end
        expect(resonse.status).to be == 403
      end

      it "delete responds with 403" do
        expect(plain_faraday_json_client.delete(admin_user_url).status).to be == 403
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
          expect(client.get(admin_url).status).to be == 403
        end
        it "post responds with 403" do
          response = client.post(user_url)
          expect(response.status).to be == 403
        end
        it "delete responds with 403" do
          expect(client.delete(admin_user_url).status).to be == 403
        end
      end
    end
  end

  context "Responds ok as admin" do
    include_context :json_client_for_authenticated_admin_user do
      context "get" do
        before :each do
          @admin = FactoryBot.create :admin
        end

        it "responds 400 with bad formatted uuid" do
          badid = Faker::Internet.slug(words: nil, glue: "-")
          response = client.get("/api/admin/admins/#{badid}")
          expect(response.status).to be == 400
        end

        it "responds 404 with non-existing id" do
          # TODO build non-existent uuid
          response = client.get("/api/admin/admins/#{user.id}")
          expect(response.status).to be == 404
        end

        describe "existing id" do
          let :response do
            client.get(admin_url)
          end

          it "responds with 200" do
            expect(response.status).to be == 200
          end

          it "has the proper data" do
            data = response.body
            expect(
              data.except("created_at", "updated_at")
            ).to eq(
              @admin.attributes.with_indifferent_access
                .except(:created_at, :updated_at)
            )
          end
        end
      end

      context "post" do
        let :response do
          client.post(user_url)
        end

        it "responds with 200" do
          expect(response.status).to be == 200
        end

        it "has the proper data" do
          data = response.body

          expect(data["user_id"]).to be == @user.id
        end
      end

      # TODO test more data

      context "delete" do
        before :each do
          @admin = FactoryBot.create :admin
        end

        let :response do
          client.delete(admin_user_url)
        end

        it "responds with 200" do
          expect(response.status).to be == 200
        end

        it "has the proper data" do
          data = response.body
          expect(
            data.except("created_at", "updated_at")
          ).to eq(
            @admin.attributes.with_indifferent_access
              .except(:created_at, :updated_at)
          )
        end
      end
    end
  end
end
