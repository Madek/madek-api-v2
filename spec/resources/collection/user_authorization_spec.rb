require "spec_helper"
require "#{Rails.root}/spec/resources/collection/shared.rb"

describe "Getting a collection resource without authentication" do
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

describe "Getting a collection resource with authentication" do
  before :example do
    @collection = FactoryBot.create(
      :collection, get_metadata_and_previews: false,
      responsible_user: FactoryBot.create(:user)
    )
    @entity = FactoryBot.create(:user, password: "password")
  end

  include_context :auth_collection_resource_via_json

  context :check_forbidden_without_required_permission do
    before :example do
      @collection.user_permissions <<
        FactoryBot.create(:collection_user_permission,
                          get_metadata_and_previews: false,
                          user: @entity)
      group = FactoryBot.create(:group)
      @entity.groups << group
      @collection.group_permissions <<
        FactoryBot.create(:collection_group_permission,
                          get_metadata_and_previews: false,
                          group: group)
    end
    it "is forbidden 403" do

      # method = :get,
      #   body = { "message" => "Not authorized for media-resource" },
      #   request_headers = { "Accept" => "application/json", "User-Agent" => "Faraday v0.17.6", "Authorization" => "Basic YWx2YXJvLmJlcm5pZXJlMDI4NTQ1MDpwYXNzd29yZA==" },
      #   response_headers = { "content-type" => "application/json; charset=utf-8", "access-control-allow-credentials" => "true", "connection" => "Close", "content-length" => "47", "server" => "http-kit", "date" => "Wed, 18 Sep 2024 11:04:01 GMT" },
      #   status = 403,
      #   reason_phrase = "Forbidden" >,

        binding.pry
      expect(response.status).to be == 403
    end
  end

  context :check_allowed_if_responsible do
    before :example do
      @collection.update! responsible_user: @entity
    end

    it "is allowed 200" do
      expect(response.status).to be == 200
    end
  end

  context :check_allowed_if_user_belongs_to_responsible_delegation do
    before do
      delegation = create(:delegation)
      delegation.users << @entity
      @collection.update!(
        responsible_user: nil,
        responsible_delegation_id: delegation.id
      )
    end

    it "is allowed 200" do
      expect(response.status).to be == 200
    end
  end

  context :check_allowed_if_user_belongs_to_group_belonging_to_responsible_delegation do
    before do
      delegation = create(:delegation)
      group = create(:group)
      delegation.groups << group
      group.users << @entity
      @collection.update!(
        responsible_user: nil,
        responsible_delegation_id: delegation.id
      )
    end

    it "is allowed 200" do
      expect(response.status).to be == 200
    end
  end

  context :check_allowed_if_user_permission do
    before :example do
      @collection.user_permissions <<
        FactoryBot.create(:collection_user_permission,
                          get_metadata_and_previews: true,
                          user: @entity)
    end

    it "is allowed 200" do
      expect(response.status).to be == 200
    end
  end

  context :check_allowed_if_group_permission do
    before :example do
      group = FactoryBot.create(:group)
      @entity.groups << group
      @collection.group_permissions <<
        FactoryBot.create(:collection_group_permission,
                          get_metadata_and_previews: true,
                          group: group)
    end

    it "is allowed 200" do
      expect(response.status).to be == 200
    end
  end
end
