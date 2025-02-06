require "spec_helper"

shared_context :preview_resource_via_plain_json do
  let :response do
    plain_faraday_json_client.get("/api-v2/previews/#{@preview.id}")
  end
end

shared_context :check_preview_resource_via_any do |ctx|
  context :via_plain_json do
    include_context :preview_resource_via_plain_json
    include_context ctx
  end
end
