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
  let(:media_entry) { FactoryBot.create(:media_entry, get_metadata_and_previews: false) }
  let(:full_text_id) { media_entry.id }

  let(:full_texts_admin_url) { "/api-v2/admin/full_texts/" }
  let(:full_texts_url) { "/api-v2/full_texts/" }
  let(:me_url) { "/api-v2/media-entries/#{full_text_id}/full_texts/" }

  let(:full_text_payload) do
    {
      media_resource_id: full_text_id,
      text: "Test Full Text"
    }
  end

  def json_request(method, url, payload = nil)
    wtoken_header_plain_faraday_json_client(token.token).public_send(method, url) do |req|
      req.body = payload.to_json if payload
      req.headers["Content-Type"] = "application/json"
    end
  end

  def insert_full_text(text)
    ActiveRecord::Base.connection.exec_query(
      "INSERT INTO full_texts (media_resource_id, text) VALUES ($1, $2)",
      "SQL", [full_text_id, text]
    )
  end

  def stored_text
    ActiveRecord::Base.connection.select_value(
      ActiveRecord::Base.sanitize_sql(["SELECT text FROM full_texts WHERE media_resource_id = ?", full_text_id])
    )
  end

  ResourceAccess::ROLES.each do |role, client_context|
    context "as #{role}" do
      include_context client_context

      ResourceAccess::GRANTING_VARIANTS.each do |variant|
        context "with access to the private media-entry via #{variant}" do
          before { ResourceAccess.setup(media_entry, user, variant) }

          it "creates, retrieves, updates, and deletes a full_text by media-entry-endpoints" do
            # CREATE
            post_response = json_request(:post, me_url, {text: "Test Full Text"})
            expect(post_response.status).to eq(200)
            expect(post_response.body["media_resource_id"]).to eq(full_text_id)

            # READ (GET)
            get_response = wtoken_header_plain_faraday_json_client_get(token.token, me_url)
            expect(get_response.status).to eq(200)
            expect(get_response.body["text"]).to eq("Test Full Text")

            get_response = wtoken_header_plain_faraday_json_client_get(token.token, "#{full_texts_url}#{full_text_id}")
            expect(get_response.status).to eq(200)
            expect(get_response.body["text"]).to eq("Test Full Text")

            # UPDATE
            put_response = json_request(:put, me_url, {text: "Updated Title"})
            expect(put_response.status).to eq(200)
            expect(put_response.body["text"]).to eq("Updated Title")

            # DELETE
            delete_response = json_request(:delete, me_url)
            expect(delete_response.status).to eq(200)

            # VERIFY DELETION
            expect(wtoken_header_plain_faraday_json_client_get(token.token, me_url).status).to eq(404)
            expect(wtoken_header_plain_faraday_json_client_get(token.token, "#{full_texts_url}#{full_text_id}").status).to eq(404)
          end

          if role == :admin
            it "creates, retrieves, updates, and deletes a full_text by user/admin-endpoints" do
              # CREATE
              post_response = json_request(:post, full_texts_admin_url, full_text_payload)
              expect(post_response.status).to eq(200)
              expect(post_response.body["media_resource_id"]).to eq(full_text_id)

              # READ (GET)
              get_response = wtoken_header_plain_faraday_json_client_get(token.token, "#{full_texts_url}#{full_text_id}")
              expect(get_response.status).to eq(200)
              expect(get_response.body["text"]).to eq("Test Full Text")

              get_response = wtoken_header_plain_faraday_json_client_get(token.token, me_url)
              expect(get_response.status).to eq(200)
              expect(get_response.body["text"]).to eq("Test Full Text")

              # UPDATE
              put_response = json_request(:put, "#{full_texts_admin_url}#{full_text_id}", full_text_payload.merge(text: "Updated Title"))
              expect(put_response.status).to eq(200)
              expect(put_response.body["text"]).to eq("Updated Title")

              # DELETE
              delete_response = json_request(:delete, "#{full_texts_admin_url}#{full_text_id}")
              expect(delete_response.status).to eq(200)

              # VERIFY DELETION
              expect(wtoken_header_plain_faraday_json_client_get(token.token, "#{full_texts_url}#{full_text_id}").status).to eq(404)
              expect(wtoken_header_plain_faraday_json_client_get(token.token, me_url).status).to eq(404)
            end
          else
            it "is forbidden on admin-endpoints" do
              insert_full_text("Test Full Text")

              expect(json_request(:post, full_texts_admin_url, full_text_payload).status).to eq(403)
              expect(json_request(:put, "#{full_texts_admin_url}#{full_text_id}", {text: "Updated Title"}).status).to eq(403)
              expect(json_request(:delete, "#{full_texts_admin_url}#{full_text_id}").status).to eq(403)
              expect(stored_text).to eq("Test Full Text")
            end
          end
        end
      end

      context "without permissions on the private media-entry" do
        before do
          ResourceAccess.setup(media_entry, user, ResourceAccess::DENYING_VARIANT)
          insert_full_text("Test Full Text")
        end

        it "is forbidden on media-entry-endpoints" do
          expect(wtoken_header_plain_faraday_json_client_get(token.token, "#{full_texts_url}#{full_text_id}").status).to eq(403)
          expect(wtoken_header_plain_faraday_json_client_get(token.token, me_url).status).to eq(403)

          list_response = wtoken_header_plain_faraday_json_client_get(token.token, full_texts_url)
          expect(list_response.status).to eq(200)
          expect(list_response.body.map { |ft| ft["media_resource_id"] }).not_to include(full_text_id)

          expect(json_request(:post, me_url, {text: "Other Text"}).status).to eq(403)
          expect(json_request(:put, me_url, {text: "Updated Title"}).status).to eq(403)
          expect(json_request(:delete, me_url).status).to eq(403)
          expect(stored_text).to eq("Test Full Text")
        end

        if role == :admin
          it "manages the full_text via admin-endpoints" do
            put_response = json_request(:put, "#{full_texts_admin_url}#{full_text_id}", {text: "Updated by admin"})
            expect(put_response.status).to eq(200)
            expect(put_response.body["text"]).to eq("Updated by admin")

            expect(json_request(:delete, "#{full_texts_admin_url}#{full_text_id}").status).to eq(200)
            expect(stored_text).to be_nil
          end
        else
          it "is forbidden on admin-endpoints" do
            expect(json_request(:put, "#{full_texts_admin_url}#{full_text_id}", {text: "Updated Title"}).status).to eq(403)
            expect(json_request(:delete, "#{full_texts_admin_url}#{full_text_id}").status).to eq(403)
            expect(stored_text).to eq("Test Full Text")
          end
        end
      end
    end
  end
end
