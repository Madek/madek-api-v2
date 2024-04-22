require "spec_helper"

context "users" do
  before :each do
    @user = FactoryBot.create :user
  end

  context "admin user" do
    include_context :json_client_for_authenticated_admin_user do
      describe "patching/updating" do
        it "works" do
          expect(
            client.patch("/api/admin/users/#{CGI.escape(@user.id)}") do |req|
              req.body = {login: "newLogin"}.to_json
              req.headers["Content-Type"] = "application/json"
            end.status
          ).to be == 200
        end

        it "works when we do no changes" do
          expect(
            client.patch("/api/admin/users/#{CGI.escape(@user.id)}") do |req|
              req.body = {login: @user.login}.to_json
              req.headers["Content-Type"] = "application/json"
            end.status
          ).to be == 200
        end

        context "patch result" do
          let :new_last_name do Faker::Name.last_name end
          let :new_first_name do Faker::Name.first_name end
          let :patch_result do
            client.patch("/api/admin/users/#{CGI.escape(@user.id)}") do |req|
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
      end
    end
  end
end
