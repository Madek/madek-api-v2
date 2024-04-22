require "spec_helper"

context "app-settings admin" do
  before :each do
    @settings = FactoryBot.create(:app_setting)
  end

  context "resource without authentication" do
    let :plain_json_response do
      plain_faraday_json_client.get("/api/admin/app-settings")
    end

    it "responds with 403" do
      expect(plain_json_response.status).to be == 403
    end
  end

  context "resource with user auth" do
    include_context :json_client_for_authenticated_user do
      it "responds with 403" do
        expect(client.get("/api/admin/app-settings").status).to be == 403
      end
    end
  end

  context "resource with admin auth" do
    include_context :json_client_for_authenticated_admin_user do
      context "read" do
        let :response do
          client.get("/api/admin/app-settings")
        end

        it "responds with 200" do
          expect(response.status).to be == 200
        end

        it "has the proper data" do
          settings = response.body
          expect(
            settings.except("created_at", "updated_at")
          ).to eq(
            @settings.attributes.with_indifferent_access
              .except(:created_at, :updated_at)
          )
        end
      end

      context "update" do
        describe "text, text array"
        # TODO hstore and json data

        let :updated_settings do
          client.put("/api/admin/app-settings") do |req|
            req.body = {
              available_locales: ["de", "en"],
              brand_logo_url: "nourl",
              # catalog_subtitles: {
              #  de: "hello cat de"
              #  en: "hello cat en"
              # },
              contexts_for_entry_extra: ["core"]
            }.to_json
            req.headers["Content-Type"] = "application/json"
          end
        end

        it "status is 200" do
          expect(updated_settings.status).to be == 200
        end

        describe "the data" do
          it "did not change other fields" do
            settings = updated_settings.body

            expect(
              settings.except("created_at", "updated_at",
                "available_locales", "brand_logo_url")
            ).to eq(
              @settings.attributes.with_indifferent_access
                .except(:created_at, :updated_at,
                  :available_locales, :brand_logo_url)
            )
          end

          it "has updated text" do
            settings = updated_settings.body

            expect(settings["available_locales"]).to be == ["de", "en"]
            expect(settings["brand_logo_url"]).to be == "nourl"
            expect(settings["contexts_for_entry_extra"]).to be == ["core"]
          end
        end
      end
    end
  end
end
