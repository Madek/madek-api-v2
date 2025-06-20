require "spec_helper"
require "#{Rails.root}/spec/resources/collection/shared.rb"

describe "Getting a collection resource without authentication" do
  include_context :json_client_for_authenticated_token_admin_no_creds do
    before :example do
      @collection = FactoryBot.create(:collection,
        get_metadata_and_previews: false)
    end

    shared_context :check_not_authenticated_without_public_permission do
      it "is forbidden 401" do
        expect(response.status).to be == 401
      end
    end

    include_context :check_collection_resource_via_any,
      :check_not_authenticated_without_public_permission
  end
end

describe "Getting a collection resource with authentication" do
  include_context :json_client_for_authenticated_token_user do
    before :example do
      @collection = FactoryBot.create(
        :collection, get_metadata_and_previews: false,
        responsible_user: FactoryBot.create(:user)
      )
      @entity = user
    end

    context :check_forbidden_without_required_permission do
      before :example do
        @collection.user_permissions <<
          FactoryBot.create(:collection_user_permission,
            get_metadata_and_previews: false,
            user: user)
        group = FactoryBot.create(:group)
        user.groups << group
        @collection.group_permissions <<
          FactoryBot.create(:collection_group_permission,
            get_metadata_and_previews: false,
            group: group)
      end
      it "is forbidden 403" do
        response = client.get("/api-v2/collections/#{CGI.escape(@collection.id)}")
        expect(response.status).to be == 403
      end
    end

    context :check_allowed_if_responsible do
      before :example do
        @collection.update! responsible_user: user
      end

      it "is allowed 200" do
        response = client.get("/api-v2/collections/#{CGI.escape(@collection.id)}")
        expect(response.status).to be == 200
      end
    end

    context :check_allowed_if_user_belongs_to_responsible_delegation do
      before do
        delegation = create(:delegation)
        delegation.users << user
        @collection.update!(
          responsible_user: nil,
          responsible_delegation_id: delegation.id
        )
      end

      it "is allowed 200" do
        response = client.get("/api-v2/collections/#{CGI.escape(@collection.id)}")
        expect(response.status).to be == 200
      end
    end

    context :check_allowed_if_user_belongs_to_group_belonging_to_responsible_delegation do
      before do
        delegation = create(:delegation)
        group = create(:group)
        delegation.groups << group
        group.users << user
        @collection.update!(
          responsible_user: nil,
          responsible_delegation_id: delegation.id
        )
      end

      it "is allowed 200" do
        response = client.get("/api-v2/collections/#{CGI.escape(@collection.id)}")
        expect(response.status).to be == 200
      end
    end

    context :check_allowed_if_user_permission do
      before :example do
        @collection.user_permissions <<
          FactoryBot.create(:collection_user_permission,
            get_metadata_and_previews: true,
            user: user)
      end

      it "is allowed 200" do
        response = client.get("/api-v2/collections/#{CGI.escape(@collection.id)}")
        expect(response.status).to be == 200
      end
    end

    context :check_allowed_if_group_permission do
      before :example do
        group = FactoryBot.create(:group)
        user.groups << group
        @collection.group_permissions <<
          FactoryBot.create(:collection_group_permission,
            get_metadata_and_previews: true,
            group: group)
      end

      it "is allowed 200" do
        response = client.get("/api-v2/collections/#{CGI.escape(@collection.id)}")
        expect(response.status).to be == 200
      end
    end
  end
end
