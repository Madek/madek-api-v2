require "spec_helper"
require Pathname(File.expand_path("../..", __FILE__)).join("shared/clients")

describe "Call swagger-endpoints" do
  context "with accept=text/html" do
    include_context :valid_session_without_csrf

    it "accesses protected resource with valid session cookie, without csrf-token" do
      resp = plain_faraday_json_client.get("/api-v2/invalid-url")
      expect(resp.status).to eq(404)

      resp = plain_faraday_json_client.get("/api-v2/auth-info/")
      expect(resp.status).to eq(200)

      resp = client.get("/api-v2/test-csrf/")
      expect(resp.status).to eq(204)

      resp = client.post("/api-v2/test-csrf/")
      expect(resp.status).to eq(403)
      expect(resp.body["msg"]).to eq("The anti-csrf-token cookie value is not set.")

      resp = client.delete("/api-v2/test-csrf/")
      expect(resp.status).to eq(403)

      resp = client.put("/api-v2/test-csrf/")
      expect(resp.status).to eq(403)

      resp = client.patch("/api-v2/test-csrf/")
      expect(resp.status).to eq(403)
    end
  end

  context "with accept=text/html" do
    include_context :valid_session_with_csrf

    it "accesses protected resource with valid session cookie, without csrf-token" do
      resp = plain_faraday_json_client.get("/api-v2/invalid-url")
      expect(resp.status).to eq(404)

      resp = plain_faraday_json_client.get("/api-v2/auth-info/")
      expect(resp.status).to eq(200)

      resp = client.get("/api-v2/test-csrf/")
      expect(resp.status).to eq(204)

      resp = client.post("/api-v2/test-csrf/")
      expect(resp.status).to eq(204)

      resp = client.delete("/api-v2/test-csrf/")
      expect(resp.status).to eq(204)

      resp = client.put("/api-v2/test-csrf/")
      expect(resp.status).to eq(204)

      resp = client.patch("/api-v2/test-csrf/")
      expect(resp.status).to eq(204)
    end

    it "responds with 404 without trailing /" do
      resp = plain_faraday_json_client.get("/api-v2/auth-info")
      expect(resp.status).to eq(404)

      resp = client.get("/api-v2/test-csrf")
      expect(resp.status).to eq(404)
    end
  end
end
