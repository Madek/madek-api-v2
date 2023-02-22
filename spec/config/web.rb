require 'json_roa/client'

def api_port
  @api_port ||= Settings.services.api.http.port
end

def api_base_url
  @api_base_url ||= "http://localhost:#{api_port}/api"
end

def json_roa_client(&block)
  JSON_ROA::Client.connect \
    api_base_url, raise_error: false, &block
end

def plain_faraday_json_client
  @plain_faraday_json_client ||= Faraday.new(
    url: api_base_url,
    headers: { accept: 'application/json' }) do |conn|
      yield(conn) if block_given?
      conn.response :json, content_type: /\bjson$/
      conn.adapter Faraday.default_adapter
    end
end

def basic_auth_plain_faraday_json_client(login, password)
  @basic_auth_plain_faraday_json_client ||= Faraday.new(
    url: api_base_url,
    headers: { accept: 'application/json' }) do |conn|
      conn.request :basic_auth, login, password
      yield(conn) if block_given?
      conn.response :json, content_type: /\bjson$/ 
      conn.adapter Faraday.default_adapter
    end
end

def basic_auth_wtoken_header_plain_faraday_json_client(login, password, token)
  @basic_auth_plain_faraday_json_client ||= Faraday.new(
    url: api_base_url,
    headers: { accept: 'application/json', 'Authorization': "token #{token}" }) do |conn|
      conn.request :basic_auth, login, password
      yield(conn) if block_given?
      conn.response :json, content_type: /\bjson$/
      conn.adapter Faraday.default_adapter
    end
end

def session_auth_plain_faraday_json_client(cookieString)
  @plain_faraday_json_client ||= Faraday.new(
    url: api_base_url,
    headers: { accept: 'application/json', Cookie: cookieString }) do |conn|
      yield(conn) if block_given?
      conn.response :json, content_type: /\bjson$/
      conn.adapter Faraday.default_adapter
    end
end

