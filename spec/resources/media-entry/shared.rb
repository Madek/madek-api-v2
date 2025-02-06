require "spec_helper"

shared_context :media_entry_resource_via_plain_json do
  let :response do
    plain_faraday_json_client.get("/api-v2/media-entry/#{media_entry.id}")
  end
end

shared_context :auth_media_entry_resource_via_plain_json do
  let :response do
    # wtoken_header_plain_faraday_json_client(user.login, user.password).get("/api-v2/media-entry/#{media_entry.id}")
    # FIXME: one of these should be used
    # wtoken_header_plain_faraday_json_client_get(@token.token, "/api-v2/media-entry/#{media_entry.id}")
    wtoken_header_plain_faraday_json_client_get(token.token, "/api-v2/media-entry/#{media_entry.id}")
  end
end

shared_context :check_media_entry_resource_via_any do |ctx|
  context :via_plain_json do
    include_context :media_entry_resource_via_plain_json
    include_context ctx
  end
end
