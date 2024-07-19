(ns madek.api.ftr-test
  (:require [clojure.test :refer :all]
            [madek.api.features.ftr-rproxy-basic :refer [RPROXY_BASIC_FEATURE_ENABLED? abort-if-no-rproxy-basic-user-for-swagger-ui]]
            [ring.mock.request :as mock]
            [clojure.string :as str]))

(defn mock-handler [request]
  {:status 200 :body "OK"})

(deftest test-abort-if-no-rproxy-basic-user-for-swagger-ui
  (testing "RPROXY_BASIC_FEATURE_ENABLED? is true and request matches conditions to return 401"
    (with-redefs [RPROXY_BASIC_FEATURE_ENABLED? true]
      (let [request (-> (mock/request :get "/api-v2/some-endpoint")
                        (mock/header "referer" "/some-referer"))
            response (abort-if-no-rproxy-basic-user-for-swagger-ui mock-handler request)]
        (is (= 401 (:status response)))
        (is (= {:message "Not authorized2"} (:body response))))))

  (testing "RPROXY_BASIC_FEATURE_ENABLED? is false and handler is called"
    (with-redefs [RPROXY_BASIC_FEATURE_ENABLED? false]
      (let [request (mock/request :get "/api-v2/some-endpoint")
            response (abort-if-no-rproxy-basic-user-for-swagger-ui mock-handler request)]
        (is (= 200 (:status response)))
        (is (= "OK" (:body response))))))

  (testing "Request path is to openapi.json and handler is called"
    (with-redefs [RPROXY_BASIC_FEATURE_ENABLED? true]
      (let [request (mock/request :get "/api-v2/openapi.json")
            response (abort-if-no-rproxy-basic-user-for-swagger-ui mock-handler request)]
        (is (= 200 (:status response)))
        (is (= "OK" (:body response))))))

  (testing "Referer is set to api-docs/index.html and handler is called"
    (with-redefs [RPROXY_BASIC_FEATURE_ENABLED? true]
      (let [request (-> (mock/request :get "/api-v2/some-endpoint")
                        (mock/header "referer" "/api-docs/index.html"))
            response (abort-if-no-rproxy-basic-user-for-swagger-ui mock-handler request)]
        (is (= 200 (:status response)))
        (is (= "OK" (:body response))))))

  (testing "Various other combinations"
    (with-redefs [RPROXY_BASIC_FEATURE_ENABLED? true]
      (let [request (mock/request :get "/api-v1/some-endpoint")
            response (abort-if-no-rproxy-basic-user-for-swagger-ui mock-handler request)]
        (is (= 200 (:status response)))
        (is (= "OK" (:body response)))))))

(run-tests)
