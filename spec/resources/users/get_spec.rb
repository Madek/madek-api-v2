require "spec_helper"

context "users" do
  before :each do
    @user = FactoryBot.create :user
  end

  context "non admin user" do
    # removed accessing users by non admins; this must be rewritten and
    # accessible fields must be restricted; something like name and email seems
    # to be OK; login is problematic; institutional id even more so since in
    # general we don't know if this information is sensitive
    #
  end

  context "admin user" do
    include_context :json_client_for_authenticated_admin_user do
      let(:admin_users_route) { "/api/admin/users" }

      context "retriving a standard user" do
        let :get_user_result do
          # client.get.relation('user').get(id: @user.id)
          client.get("#{admin_users_route}/#{CGI.escape(@user.id)}")
        end

        it "works" do
          expect(get_user_result.status).to be == 200
        end

        it "has the proper data, sans :searchable and :previous_id" do
          expect(get_user_result.body.with_indifferent_access
              .slice(:id, :email, :login, :person_id)).to be ==
                @user.attributes.with_indifferent_access
                  .slice(:id, :email, :login, :person_id)
        end
      end

      context "a user (with a naughty institutional_id)" do
        # removed: getting a user by institutional id as the uid would not be
        # safe; the combination of (institution, institutional_id) would be
        # unique and could be used via query params; it would still be unsafe to
        # combine to to in the uid route parameter
      end

      # TODO: write tests for in/valid email addresses
      context "a user (with a naughty email)" do
        before :each do
          valid_email_addresses = [
            # 'Abc@defexample.com',
            # 'Abc\@def@example.com',
            #
            # TODO test users get by email
            # 'Fred\ Bloggs@example.com',
            "Joe.Blow@example.com",
            # '"Abc@def"example.com',
            "Abc@defexample.com",
            # '"Abc@def"@example.com',
            # TODO test users get by email
            # '"Fred Bloggs"@example.com',
            # 'customer/department=shipping@example.com',
            # '$A12345@example.com',
            # '!def!xyz%abc@example.com',
            "_somename@example.com"
          ]
          @users = valid_email_addresses.map do |a|
            FactoryBot.create :user, email: a
          end
        end
        it "can be retrieved by the email_address" do
          @users.each do |user|
            expect(
              client.get("#{admin_users_route}/#{CGI.escape(user.email)}").status
            ).to be == 200
            expect(
              client.get("#{admin_users_route}/#{CGI.escape(user.email)}").body["id"]
            ).to be == user["id"]
          end
        end
      end
    end
  end
end
