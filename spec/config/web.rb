require "json_roa/client"

def api_port
  @api_port ||= ENV["API_V2_HTTP_PORT"].presence || 3104
end

def api_base_url
  @api_base_url ||= "http://localhost:#{api_port}/api-v2"
end

def json_roa_client(&)
  JSON_ROA::Client.connect(
    api_base_url, raise_error: false, &
  )
end

def plain_faraday_json_client
  @plain_faraday_json_client ||= Faraday.new(
    url: api_base_url,
    headers: {accept: "application/json"}
  ) do |conn|
    yield(conn) if block_given?
    conn.response :json, content_type: /\bjson$/
    conn.adapter Faraday.default_adapter
  end
end

# @deprecated
# def wtoken_header_plain_faraday_json_client(login, password)
#   @wtoken_header_plain_faraday_json_client ||= Faraday.new(
#     url: api_base_url,
#     headers: {accept: "application/json"}
#   ) do |conn|
#     conn.request :basic_auth, login, password
#     yield(conn) if block_given?
#     conn.response :json, content_type: /\bjson$/
#     conn.adapter Faraday.default_adapter
#   end
# end
#
# @deprecated
# def basic_auth_wtoken_header_plain_faraday_json_client(login, password, token)
#   @wtoken_header_plain_faraday_json_client ||= Faraday.new(
#     url: api_base_url,
#     headers: {accept: "application/json", Authorization: "token #{token}"}
#   ) do |conn|
#     conn.request :basic_auth, login, password
#     yield(conn) if block_given?
#     conn.response :json, content_type: /\bjson$/
#     conn.adapter Faraday.default_adapter
#   end
# end

def wtoken_header_plain_faraday_json_client(token)
  @plain_faraday_json_client ||= Faraday.new(
    url: api_base_url,
    headers: {accept: "application/json", Authorization: "token #{token}"}
  ) do |conn|
    yield(conn) if block_given?
    conn.response :json, content_type: /\bjson$/
    conn.adapter Faraday.default_adapter
  end
end

def wtoken_header_plain_faraday_json_client_get(token, url)
  token_header_plain_faraday_json_client(:get, url, token)
end

def wtoken_header_plain_faraday_json_client_post(token, url, body: nil)
  token_header_plain_faraday_json_client(:post, url, token, body: body)
end

def wtoken_header_plain_faraday_json_client_delete(token, url)
  token_header_plain_faraday_json_client(:delete, url, token)
end

def wtoken_header_plain_faraday_json_client_put(token, url)
  token_header_plain_faraday_json_client(:put, url, token)
end

def token_header_plain_faraday_json_client(method, url, token, body: nil, headers: {})
  Faraday.new(url: api_base_url) do |conn|
    conn.headers["Authorization"] = "token #{token}"
    conn.headers["Content-Type"] = "application/json"
    conn.headers.update(headers)
    conn.response :json, content_type: /\bjson$/
    conn.adapter Faraday.default_adapter

    yield(conn) if block_given?
  end.public_send(method, url) do |req|
    req.body = body.to_json if body
  end
end

def session_auth_plain_faraday_json_client(cookie_string)
  @plain_faraday_json_client ||= Faraday.new(
    url: api_base_url,
    headers: {accept: "application/json", Cookie: cookie_string}
  ) do |conn|
    yield(conn) if block_given?
    conn.response :json, content_type: /\bjson$/
    conn.adapter Faraday.default_adapter
  end
end
