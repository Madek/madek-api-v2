require 'spec_helper'


shared_context :collection_resource_via_json do
  let :response do
    plain_faraday_json_client.get("/api/collections/#{CGI.escape(@collection.id)}")
  end
end


shared_context :auth_collection_resource_via_json do
  let :client do
    basic_auth_plain_faraday_json_client(@entity.login, @entity.password)
  end
  
  let :response do
    client.get("/api/collections/#{CGI.escape(@collection.id)}")
  end
end

shared_context :collection_resource_via_plain_json do
  let :response do
    plain_faraday_json_client.get("/api/collections/#{@collection.id}")
  end
end

shared_context :check_collection_resource_via_any do |ctx|
  context :via_plain_json do
    include_context :collection_resource_via_plain_json
    include_context ctx
  end
end
