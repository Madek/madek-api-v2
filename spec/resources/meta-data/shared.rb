require "spec_helper"

ROUNDS = begin
  Integer(ENV["ROUNDS"].presence)
rescue
  25
end

shared_context :meta_datum_for_media_entry do |_ctx|
  let :media_resource do
    user = FactoryBot.create(:user)
    FactoryBot.create :media_entry, creator: user, responsible_user: user
  end
end

shared_context :meta_datum_for_random_resource_type do |_ctx|
  let :media_resource do
    user = FactoryBot.create(:user)
    if rand < 1.0 / 2
      FactoryBot.create :media_entry, creator: user, responsible_user: user
    else
      FactoryBot.create :collection, creator: user, responsible_user: user
    end
  end

  def meta_datum(type)
    case media_resource
    when MediaEntry
      FactoryBot.create "meta_datum_#{type}",
        media_entry: media_resource
    when Collection
      FactoryBot.create "meta_datum_#{type}",
        collection: media_resource
    end
  end
end

shared_context :random_resource_type do |_ctx|
  let :media_resource do
    user = FactoryBot.create(:user)
    if rand < 1.0 / 2
      FactoryBot.create :media_entry, creator: user, responsible_user: user
    else
      FactoryBot.create :collection, creator: user, responsible_user: user
    end
  end

  def meta_key(type)
    FactoryBot.create "meta_key_#{type}"
  end

  def test_resource_id(md)
    case media_resource
    when MediaEntry
      expect(md["media_entry_id"]).to be == media_resource.id
    when Collection
      expect(md["collection_id"]).to be == media_resource.id
    end
  end

  def resource_url_typed_ided_personed_positioned(meta_key_id, type, id, person_id, position)
    mkid = CGI.escape(meta_key_id)
    case media_resource
    when MediaEntry
      "/api/media-entry/#{media_resource.id}/meta-datum/#{mkid}/#{type}/#{id}/#{person_id}/#{position}"
    when Collection
      "/api/collection/#{media_resource.id}/meta-datum/#{mkid}/#{type}/#{id}/#{person_id}/#{position}"
    end
  end

  def resource_url_typed_ided_personed(meta_key_id, type, id, person_id)
    mkid = CGI.escape(meta_key_id)
    case media_resource
    when MediaEntry
      "/api/media-entry/#{media_resource.id}/meta-datum/#{mkid}/#{type}/#{id}/#{person_id}"
    when Collection
      "/api/collection/#{media_resource.id}/meta-datum/#{mkid}/#{type}/#{id}/#{person_id}"
    end
  end

  def resource_url_typed_ided(meta_key_id, type, id)
    mkid = CGI.escape(meta_key_id)
    case media_resource
    when MediaEntry
      "/api/media-entry/#{media_resource.id}/meta-datum/#{mkid}/#{type}/#{id}"
    when Collection
      "/api/collection/#{media_resource.id}/meta-datum/#{mkid}/#{type}/#{id}"
    end
  end

  def resource_url_typed(meta_key_id, type)
    mkid = CGI.escape(meta_key_id)
    case media_resource
    when MediaEntry
      "/api/media-entry/#{media_resource.id}/meta-datum/#{mkid}/#{type}"
    when Collection
      "/api/collection/#{media_resource.id}/meta-datum/#{mkid}/#{type}"
    end
  end

  def resource_url(meta_key_id)
    mkid = CGI.escape(meta_key_id)
    case media_resource
    when MediaEntry
      "/api/media-entry/#{media_resource.id}/meta-datum/#{mkid}"
    when Collection
      "/api/collection/#{media_resource.id}/meta-datum/#{mkid}"
    end
  end
end
