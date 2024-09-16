require "spec_helper"

shared_context :collection_resource_via_json do
  let :response do
    plain_faraday_json_client.get("/api-v2/collection/#{CGI.escape(@collection.id)}")
  end
end

shared_context :check_collection_resource_via_any do |ctx|
  context :via_plain_json do
    include_context :collection_resource_via_json
    include_context ctx
  end
end
