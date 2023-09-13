require 'spec_helper'

ROUNDS = begin
           Integer(ENV['ROUNDS'].presence)
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
    case
    when rand < 1.0 / 2
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
    case
    when rand < 1.0 / 2
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
      expect(md['media_entry_id']).to be == media_resource.id
    when Collection
      expect(md['collection_id']).to be == media_resource.id
    end  
  end
  def resource_url_typed_ided_personed_positioned(meta_key_id, type, id, person_id, position)
    case media_resource
    when MediaEntry
      "/api/media-entry/#{media_resource.id}/meta-data/#{meta_key_id}/#{type}/#{id}/#{person_id}/#{position}"
    when Collection
      "/api/collection/#{media_resource.id}/meta-data/#{meta_key_id}/#{type}/#{id}/#{person_id}/#{position}"
    end
  end

  def resource_url_typed_ided_personed(meta_key_id, type, id, person_id)
    case media_resource
    when MediaEntry
      "/api/media-entry/#{media_resource.id}/meta-data/#{meta_key_id}/#{type}/#{id}/#{person_id}"
    when Collection
      "/api/collection/#{media_resource.id}/meta-data/#{meta_key_id}/#{type}/#{id}/#{person_id}"
    end
  end

  def resource_url_typed_ided(meta_key_id, type, id)
    case media_resource
    when MediaEntry
      "/api/media-entry/#{media_resource.id}/meta-data/#{meta_key_id}/#{type}/#{id}"
    when Collection
      "/api/collection/#{media_resource.id}/meta-data/#{meta_key_id}/#{type}/#{id}"
    end
  end

  def resource_url_typed(meta_key_id, type)
    case media_resource
    when MediaEntry
      "/api/media-entry/#{media_resource.id}/meta-data/#{meta_key_id}/#{type}"
    when Collection
      "/api/collection/#{media_resource.id}/meta-data/#{meta_key_id}/#{type}"
    end
  end

  def resource_url(meta_key_id)
    case media_resource
    when MediaEntry
      "/api/media-entry/#{media_resource.id}/meta-data/#{meta_key_id}"
    when Collection
      "/api/collection/#{media_resource.id}/meta-data/#{meta_key_id}"
    end
  end
end
