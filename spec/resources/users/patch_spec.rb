require "spec_helper"

context "users" do
  before :each do
    @user = FactoryBot.create :user
  end

  context "admin user" do
    include_context :json_client_for_authenticated_token_admin do
      describe "patching/updating" do
        it "works" do
          expect(
            client.patch("/api-v2/admin/users/#{CGI.escape(@user.id)}") do |req|
              req.body = {login: "newLogin"}.to_json
              req.headers["Content-Type"] = "application/json"
            end.status
          ).to be == 200
        end

        it "works when we do no changes" do
          expect(
            client.patch("/api-v2/admin/users/#{CGI.escape(@user.id)}") do |req|
              req.body = {login: @user.login}.to_json
              req.headers["Content-Type"] = "application/json"
            end.status
          ).to be == 200
        end

        context "patch result" do
          let(:new_last_name) { Faker::Name.last_name }
          let(:new_first_name) { Faker::Name.first_name }
          let :patch_result do
            client.patch("/api-v2/admin/users/#{CGI.escape(@user.id)}") do |req|
              req.body = {
                email: "new@mail.com",
                login: "newLogin",
                last_name: new_last_name,
                first_name: new_first_name
              }.to_json
              req.headers["Content-Type"] = "application/json"
            end
          end

          it "contains the update" do
            expect(patch_result.body["email"]).to be == "new@mail.com"
            expect(patch_result.body["login"]).to be == "newLogin"
            expect(patch_result.body["last_name"]).to be == new_last_name
            expect(patch_result.body["first_name"]).to be == new_first_name
          end
        end

        context "patch result with focus on active_until" do
          let(:new_last_name) { Faker::Name.last_name }
          let(:new_first_name) { Faker::Name.first_name }

          it "works with different timestamp-formats " do
            ["2025-06-26T16:30:46+02:00",
              "2025-06-26T16:30:46.926173+02:00",
              "2025-06-26T16:30:46Z",
              "2025-06-26T16:30:46.123456789Z",
              "2025-06-26T16:30:46.926173-05:00",
              "2025-06-26T16:30:46.123+00:00"].each do |timestamp|
              expect(client.patch("/api-v2/admin/users/#{CGI.escape(@user.id)}") do |req|
                req.body = {
                  email: "new@mail.com",
                  login: "newLogin",
                  last_name: new_last_name,
                  first_name: new_first_name,
                  active_until: timestamp
                }.to_json
                req.headers["Content-Type"] = "application/json"
              end.status).to be == 200
            end
          end

          it "rejects request if active_date is nil " do
            [nil].each do |timestamp|
              expect(client.patch("/api-v2/admin/users/#{CGI.escape(@user.id)}") do |req|
                req.body = {
                  email: "new@mail.com",
                  login: "newLogin",
                  last_name: new_last_name,
                  first_name: new_first_name,
                  active_until: timestamp
                }.to_json
                req.headers["Content-Type"] = "application/json"
              end.status).to be == 422
            end
          end
        end
      end
    end
  end
end
