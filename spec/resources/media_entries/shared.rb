require "spec_helper"
require "base32/crockford"

shared_context :bunch_of_media_entries do
  let :users_count do
    5
  end

  let :media_entries_count do
    30
  end

  let :users do
    (1..users_count).map do
      FactoryBot.create :user
    end
  end

  let :media_entries do
    (1..media_entries_count).map do
      me = FactoryBot.create :media_entry,
        responsible_user: users[rand(users_count)],
        is_published: (rand <= 0.9),
        get_metadata_and_previews: true, # (rand <= 0.8),
        get_full_size: true # (rand <= 0.3)

      FactoryBot.create :media_file_for_image, media_entry: me
      me
    end
  end

  let :create_5_media_entries do
    @collection = FactoryBot.create :collection

    media_types = [
      :media_file_for_image,
      :media_file_for_audio,
      :media_file_for_movie,
      :media_file_for_document,
      :media_file_for_other
    ]

    (1..7).map do |i|
      vocabulary = create(:vocabulary,
        id: Faker::Lorem.characters(number: 10))

      user = users[rand(users_count)]

      me = FactoryBot.create :media_entry,
        responsible_user: user,
        is_published: (rand <= 0.9),
        get_metadata_and_previews: true

      FactoryBot.create :confidential_link,
        created_at: Time.now,
        user: user,
        resource: me

      title_meta_key = FactoryBot.create(:meta_key_text,
        id: "#{vocabulary.id}:#{Faker::Lorem.characters(number: 20)}",
        vocabulary: vocabulary)

      @meta_datum_text = FactoryBot.create :meta_datum_text,
        media_entry: me

      @meta_datum_text = FactoryBot.create :meta_datum_people,
        media_entry: me

      @meta_datum_text = FactoryBot.create :meta_datum_title,
        media_entry: me

      @meta_datum_text = FactoryBot.create :meta_datum_roles,
        media_entry: me

      FactoryBot.create(:meta_datum_text,
        media_entry: me,
        meta_key: title_meta_key,
        value: "Title #{i}")

      me.update(get_full_size: rand <= 0.3)

      FactoryBot.create :media_file_for_image, media_entry: me

      media_type = media_types[i % media_types.size]

      FactoryBot.create media_type, media_entry: me

      if i == 1
        @meta_key_keywords = FactoryBot.create :meta_key_keywords
        @meta_datum = FactoryBot.create :meta_datum_keywords,
          media_entry: me,
          meta_key: @meta_key_keywords
      end

      me
    end
  end

  let :collection do
    coll = FactoryBot.create :collection
    media_entries.each do |me|
      if me.is_published && (rand <= 0.75)
        FactoryBot.create :collection_media_entry_arc,
          collection: coll, media_entry: me
      end
    end
    coll
  end
end

shared_examples "ordering by created_at" do |direction = nil|
  before do
    expect(media_entries.size).to eq(30)
    media_entries.map do |me|
    end
  end

  def media_entries_created_at(order = nil)
    # to_datetime.strftime('%Q').to_i => int with ms precision
    client.get("/api-v2/media-entries", {"order" => order})
      .body.with_indifferent_access["media_entries"]
      .map { |me| MediaEntry.unscoped.find(me["id"]) }
      .map { |me| me.created_at.to_datetime.strftime("%Q").to_i }
  end

  if [nil, "asc"].include?(direction)
    specify "ascending order" do
      media_entries_created_at("asc").each_cons(2) do |ca_pair|
        expect(ca_pair.first < ca_pair.second).to be true
      end
    end
  end

  if [nil, "desc"].include?(direction)
    specify "descending order" do
      media_entries_created_at("desc").each_cons(2) do |ca_pair|
        expect(ca_pair.first > ca_pair.second).to be true
      end
    end
  end
end

shared_examples "ordering by madek_core:title" do |direction = nil|
  let(:meta_key_title) do
    with_disabled_triggers do
      MetaKey.find_by(id: "madek_core:title") || FactoryBot.create(:meta_key_core_title)
    end
  end

  before do
    media_entries.map do |me|
      FactoryBot.create(:meta_datum_text,
        media_entry: me, meta_key: meta_key_title, string: Faker::Lorem.characters(number: 16))
    end
  end

  def titles(direction = nil)
    raise ArgumentError unless [nil, "asc", "desc"].include?(direction)

    order = direction ? "title_#{direction}" : direction
    resource(order)
      .body.with_indifferent_access["media_entries"]
      .map { |me| MediaEntry.unscoped.find(me["id"]) }
      .map(&:title)
  end

  if [nil, "asc"].include?(direction)
    specify "ascending order" do
      titles("asc").each_cons(2) do |pair|
        puts pair.inspect unless pair.first < pair.last
        expect(pair.first < pair.last).to be true
      end
    end
  end

  if [nil, "desc"].include?(direction)
    specify "descending order" do
      titles("desc").each_cons(2) do |pair|
        expect(pair.first > pair.last).to be true
      end
    end
  end
end

shared_examples "ordering by last_change" do
  before do
    expect(media_entries.size).to eq(30)
    media_entries.map do |me|
    end
  end

  def edit_session_updated_ats
    resource("last_change")
      .body.with_indifferent_access["media_entries"]
      .map { |me| MediaEntry.unscoped.find(me["id"]) }
      .map { |me| me.edit_session_updated_at.to_datetime.strftime("%Q").to_i }
  end

  specify "ascending order" do
    edit_session_updated_ats.each_cons(2) do |pair|
      expect(pair.first < pair.last).to be true
    end
  end
end
