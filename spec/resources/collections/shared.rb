require "spec_helper"

shared_context :bunch_of_collections do
  let :users_count do
    5
  end

  let :collections_count do
    30
  end

  let :users do
    (1..users_count).map do
      FactoryBot.create :user
    end
  end

  let :collections do
    (1..collections_count).map do
      FactoryBot.create :collection,
        responsible_user: users[rand(users_count)],
        get_metadata_and_previews: (rand <= 0.8)
    end
  end
end

shared_context :full_setup_for_collections do
  before :each do
    Collection.find_each do |c|
      c.user_permissions.destroy_all
      c.destroy
    end

    MediaEntry.destroy_all
  end

  let :p1 do
    FactoryBot.create(:person)
  end

  let :user do
    user = FactoryBot.create(:user, person_id: p1.id)
    FactoryBot.create(:admin, user: user)
    user
  end

  let :client do
    puts "user.login: #{user.login}"
    puts "user.password: #{user.password}"
    wtoken_header_plain_faraday_json_client(@token.token)
  end

  let :media_entries do
    MediaEntry.destroy_all
    me = FactoryBot.create(
      :media_entry,
      responsible_user: user,
      is_published: true,
      get_metadata_and_previews: true,
      get_full_size: true
    )
    FactoryBot.create :media_file_for_image, media_entry: me
    [me]
  end

  let :collection_4 do
    FactoryBot.create(:collection, get_metadata_and_previews: true)
  end

  let :media_entry_with_media do
    me = FactoryBot.create(
      :media_entry,
      responsible_user: user,
      is_published: true,
      get_metadata_and_previews: true,
      get_full_size: true
    )
    FactoryBot.create :media_file_for_image, media_entry: me

    meta_key_id = "test:#{Faker::Lorem.characters(number: 8)}"
    FactoryBot.create(
      :meta_datum_people,
      people: [p1],
      meta_key: FactoryBot.create(
        :meta_key,
        meta_datum_object_type: "MetaDatum::People",
        allowed_people_subtypes: ["Person"],
        id: meta_key_id
      ),
      media_entry: me,
      collection: collection_4
    )
    [me]
  end

  let :parent_collection do
    parent_collection = FactoryBot.create(:collection)
    collection_1 = FactoryBot.create(:collection, get_metadata_and_previews: true)
    collection_2 = FactoryBot.create(:collection, get_metadata_and_previews: true)
    collection_3 = FactoryBot.create(:collection, get_metadata_and_previews: true)

    [collection_1, collection_2, collection_3, collection_4].each do |c|
      parent_collection.collections << c
    end

    media_entries.each do |me|
      if me.is_published
        FactoryBot.create(:collection_media_entry_arc, collection: collection_2, media_entry: me)
      end
    end

    FactoryBot.create(:meta_key_title)
    FactoryBot.create(:meta_datum_text, collection: collection_1, value: "Blah", meta_key_id: "test:title")

    meta_datum_keywords = FactoryBot.create(:meta_datum_keywords, keywords: [FactoryBot.create(:keyword), FactoryBot.create(:keyword, term: "gaura nitai bol")])
    collection_3.meta_data << meta_datum_keywords

    media_entry_with_media.each do |me|
      if me.is_published
        FactoryBot.create(:collection_media_entry_arc, collection: collection_4, media_entry: me)
      end
    end

    parent_collection
  end
end
