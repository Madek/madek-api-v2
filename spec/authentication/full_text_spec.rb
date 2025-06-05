require "spec_helper"

shared_context :user_entity do |ctx|
  context "for Database User" do
    before :each do
      @entity = FactoryBot.create :user, password: "TOPSECRET"
    end

    let(:entity_type) { "User" }

    include_context ctx if ctx
  end
end

describe "Access full_texts " do
  describe "with user-token" do
    include_context :json_client_for_authenticated_token_user

    context "GET requests to full_texts" do
      it "allows access to full_texts without pagination" do
        url = "/api-v2/full_texts/"
        response = wtoken_header_plain_faraday_json_client_get(token.token, url)

        expect(response.status).to eq(200)
        expect(response.body).to be_an(Array)
      end

      it "allows access to full_texts with pagination" do
        url = "/api-v2/full_texts/?page=1&size=5"
        response = wtoken_header_plain_faraday_json_client_get(token.token, url)

        expect(response.status).to eq(200)
        expect(response.body["data"]).to be_an(Array)
        expect(response.body["pagination"]).to be_a(Hash)
      end
    end
  end
end

context "Workflow: create, update, delete full_texts" do
  include_context :json_client_for_authenticated_token_admin

  let(:full_text_id) {
    me = FactoryBot.create(:media_entry)
    me.id
  }

  let(:full_texts_admin_url) { "/api-v2/admin/full_texts/" }
  let(:full_texts_url) { "/api-v2/full_texts/" }
  let(:me_url) { "/api-v2/media-entries/#{full_text_id}/full_texts/" }

  let(:full_text_payload) do
    {
      media_resource_id: full_text_id,
      text: "Test Full Text"
    }
  end

  it "creates, retrieves, updates, and deletes a full_text by user/admin-endpoints" do
    # CREATE
    post_response = wtoken_header_plain_faraday_json_client(token.token).post(full_texts_admin_url) do |req|
      req.body = full_text_payload.to_json
      req.headers["Content-Type"] = "application/json"
    end

    expect(post_response.status).to eq(200)
    full_text_id = post_response.body["media_resource_id"]
    expect(full_text_id).not_to be_nil

    # READ (GET)
    get_response = wtoken_header_plain_faraday_json_client_get(token.token, "#{full_texts_url}#{full_text_id}")
    expect(get_response.status).to eq(200)
    expect(get_response.body["text"]).to eq("Test Full Text")

    get_response = wtoken_header_plain_faraday_json_client_get(token.token, me_url)
    expect(get_response.status).to eq(200)
    expect(get_response.body["text"]).to eq("Test Full Text")

    # UPDATE
    updated_payload = full_text_payload.merge(text: "Updated Title")
    put_response = wtoken_header_plain_faraday_json_client(token.token).put("#{full_texts_admin_url}#{full_text_id}") do |req|
      req.body = updated_payload.to_json
      req.headers["Content-Type"] = "application/json"
    end

    expect(put_response.status).to eq(200)
    expect(put_response.body["text"]).to eq("Updated Title")

    # DELETE
    delete_response = wtoken_header_plain_faraday_json_client(token.token).delete("#{full_texts_admin_url}#{full_text_id}")
    expect(delete_response.status).to eq(200)

    # VERIFY DELETION
    get_after_delete = wtoken_header_plain_faraday_json_client_get(token.token, "#{full_texts_url}#{full_text_id}")
    expect(get_after_delete.status).to eq(404)

    get_response = wtoken_header_plain_faraday_json_client_get(token.token, me_url)
    expect(get_response.status).to eq(404)
  end

  it "creates, retrieves, updates, and deletes a full_text by media-entry-endpoints" do
    # CREATE
    post_response = wtoken_header_plain_faraday_json_client(token.token).post(full_texts_admin_url) do |req|
      req.body = full_text_payload.to_json
      req.headers["Content-Type"] = "application/json"
    end

    expect(post_response.status).to eq(200)
    full_text_id = post_response.body["media_resource_id"]
    expect(full_text_id).not_to be_nil

    # READ (GET)
    get_response = wtoken_header_plain_faraday_json_client_get(token.token, me_url)
    expect(get_response.status).to eq(200)
    expect(get_response.body["text"]).to eq("Test Full Text")

    get_response = wtoken_header_plain_faraday_json_client_get(token.token, me_url)
    expect(get_response.status).to eq(200)
    expect(get_response.body["text"]).to eq("Test Full Text")

    # UPDATE
    put_response = wtoken_header_plain_faraday_json_client(token.token).put(me_url) do |req|
      req.body = {text: "Updated Title"}.to_json
      req.headers["Content-Type"] = "application/json"
    end
    expect(put_response.status).to eq(200)
    expect(put_response.body["text"]).to eq("Updated Title")

    # DELETE
    delete_response = wtoken_header_plain_faraday_json_client(token.token).delete(me_url)
    expect(delete_response.status).to eq(200)

    # VERIFY DELETION
    get_response = wtoken_header_plain_faraday_json_client_get(token.token, me_url)
    expect(get_response.status).to eq(404)
  end
end
