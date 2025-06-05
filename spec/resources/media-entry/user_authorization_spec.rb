require "spec_helper"
require Pathname(File.expand_path("..", __FILE__)).join("shared")

describe "Getting a media-entry resource without authentication" do
  let :media_entry do
    FactoryBot.create(:media_entry,
      get_metadata_and_previews: false)
  end

  shared_context :check_not_authenticated_without_public_permission do
    it "is forbidden 401" do
      expect(response.status).to be == 401
    end
  end

  include_context :check_media_entry_resource_via_any,
    :check_not_authenticated_without_public_permission
end

describe "Getting a media-entry resource with authentication" do
  include_context :json_client_for_authenticated_token_owner_user do
    before :example do
      @media_entry = FactoryBot.create(
        :media_entry, get_metadata_and_previews: false,
        responsible_user: FactoryBot.create(:user)
      )
    end

    context :check_forbidden_without_required_permission do
      before :example do
        @media_entry.user_permissions <<
          FactoryBot.create(:media_entry_user_permission,
            get_metadata_and_previews: false,
            user: user_entity)
        group = FactoryBot.create(:group)
        user_entity.groups << group
        @media_entry.group_permissions <<
          FactoryBot.create(:media_entry_group_permission,
            get_metadata_and_previews: false,
            group: group)
      end

      it "is forbidden 403" do
        response = user_client.get("/api-v2/media-entries/#{@media_entry.id}")
        expect(response.status).to be == 403
      end

      it "is forbidden 403, no read access" do
        response = user_client_no_creds.get("/api-v2/media-entries/#{@media_entry.id}")
        expect(response.status).to be == 403
      end
    end

    context :check_allowed_if_responsible_user do
      before :example do
        @media_entry.update! responsible_user: user_entity
      end

      it "is allowed 200" do
        response = user_client.get("/api-v2/media-entries/#{@media_entry.id}")
        expect(response.status).to be == 200
      end
    end
  end

  context :check_allowed_if_user_belongs_to_responsible_delegation do
    include_context :json_client_for_authenticated_token_owner_user do
      before do
        delegation = create(:delegation)
        delegation.users << user_entity
        @media_entry.update!(
          responsible_user: nil,
          responsible_delegation_id: delegation.id
        )
      end

      it "is allowed 200" do
        response = user_client.get("/api-v2/media-entries/#{@media_entry.id}")
        expect(response.status).to be == 200
      end
    end
  end

  context :check_allowed_if_user_belongs_to_group_belonging_to_responsible_delegation do
    before do
      delegation = create(:delegation)
      group = create(:group)
      delegation.groups << group
      group.users << user_entity
      @media_entry.update!(
        responsible_user: nil,
        responsible_delegation_id: delegation.id
      )
    end

    it "is allowed 200" do
      response = user_client.get("/api-v2/media-entries/#{@media_entry.id}")
      expect(response.status).to be == 200
    end
  end

  context :check_allowed_if_user_permission do
    before :example do
      @media_entry.user_permissions <<
        FactoryBot.create(:media_entry_user_permission,
          get_metadata_and_previews: true,
          user: user_entity)
    end

    it "is allowed 200" do
      response = user_client.get("/api-v2/media-entries/#{@media_entry.id}")
      expect(response.status).to be == 200
    end
  end

  context :check_allowed_if_group_permission do
    before :example do
      group = FactoryBot.create(:group)
      user_entity.groups << group
      @media_entry.group_permissions <<
        FactoryBot.create(:media_entry_group_permission,
          get_metadata_and_previews: true,
          group: group)
    end

    it "is allowed 200" do
      response = user_client.get("/api-v2/media-entries/#{@media_entry.id}")
      expect(response.status).to be == 200
    end
  end
end
