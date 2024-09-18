require "spec_helper"

shared_context :user_entity do |ctx|
  context "for Database User" do
    before :each do
      @entity = FactoryBot.create :user, password: "TOPSECRET"
    end
    let :entity_type do
      "User"
    end
    include_context ctx if ctx
  end
end

shared_context :admin_entity do |ctx|
  context "for Database User" do
    before :each do
      @entity = FactoryBot.create :user, password: "TOPSECRET"
      FactoryBot.create :admin, user: @entity
    end
    let :entity_type do
      "User"
    end
    include_context ctx if ctx
  end
end

shared_context :test_proper_user_basic_auth do
  context "with basicAuth-user" do
    let :client do
      basic_auth_plain_faraday_json_client(@entity.login, @entity.password)
    end

    it "combined with other filter option" do
      @collection = create_collections_3_visible_0_deleted @entity

      response = client.get("/api-v2/collections?collection_id=" + @collection.id).body.with_indifferent_access["collections"]
      expect(response.count).to be == 3
      response.each do |me|
        collection = Collection.unscoped.find(me["id"])
        expect(@collection.collections).to include collection
      end
    end

    it "combined with other filter option2" do
      @collection = create_collections_2_visible_2_deleted @entity

      response = client.get("/api-v2/collections?collection_id=" + @collection.id).body.with_indifferent_access["collections"]
      expect(response.count).to be == 2
      response.each do |me|
        collection = Collection.unscoped.find(me["id"])
        expect(@collection.collections).to include collection
      end
    end
  end
end

#######################################################################################################################

# Admin tests

shared_context :test_proper_admin_basic_auth do
  context "with admin-user" do
    let :client do
      basic_auth_plain_faraday_json_client(@entity.login, @entity.password)
    end

    it "combined with other filter option" do
      @collection = create_collections_3_visible_0_deleted @entity

      response = client.get("/api-v2/collections?collection_id=" + @collection.id).body.with_indifferent_access["collections"]
      expect(response.count).to be == 3
      response.each do |me|
        collection = Collection.unscoped.find(me["id"])
        expect(@collection.collections).to include collection
      end
    end

    it "combined with other filter option" do
      @collection = create_collections_2_visible_2_deleted @entity

      response = client.get("/api-v2/collections?collection_id=" + @collection.id).body.with_indifferent_access["collections"]
      expect(response.count).to be == 2
      response.each do |me|
        collection = Collection.unscoped.find(me["id"])
        expect(@collection.collections).to include collection
      end
    end
  end
end

#######################################################################################################################

describe "/auth-info resource" do
  context "without any authentication" do
    context "via json" do
      let :response do
        plain_faraday_json_client.get("/api-v2/auth-info")
      end

      it "responds with not authorized 401" do
        expect(response.status).to be == 401
      end
    end
  end

  context "Basic Authentication" do
    include_context :user_entity, :test_proper_user_basic_auth
    include_context :admin_entity, :test_proper_admin_basic_auth
  end
end

def create_collections_3_visible_0_deleted(user)
  @collection = FactoryBot.create(:collection)

  collection_1 = FactoryBot.create(:collection)
  collection_2 = FactoryBot.create(:collection)
  collection_3 = FactoryBot.create(:collection)

  collection_3.user_permissions <<
    FactoryBot.create(:collection_user_permission,
      user: user,
      get_metadata_and_previews: true)

  [collection_1, collection_2, collection_3].each do |c|
    @collection.collections << c
  end

  @collection
end

def create_collections_2_visible_2_deleted(user)
  @collection = FactoryBot.create(:collection)

  collection_1 = FactoryBot.create(:collection,
    deleted_at: Time.now)

  collection_2 = FactoryBot.create(:collection,
    deleted_at: Time.now + 1.day)

  collection_3 = FactoryBot.create(:collection,
    deleted_at: Time.now - 1.day)

  collection_4 = FactoryBot.create(:collection)

  collection_3.user_permissions <<
    FactoryBot.create(:collection_user_permission,
      user: user,
      get_metadata_and_previews: true)

  [collection_1, collection_2, collection_3, collection_4].each do |c|
    @collection.collections << c
  end

  @collection
end