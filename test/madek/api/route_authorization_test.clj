(ns madek.api.route-authorization-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [madek.api.route-authorization :as ra]
   [madek.api.web :as web]
   [reitit.ring :as rr]))

(def router
  (rr/router web/get-router-data-all web/get-router-options))

(deftest every-route-is-protected-or-explicitly-allowed
  (is (empty? (ra/unprotected-routes router))
      "Add authorization middleware or an entry in explicitly-unprotected-routes"))

(deftest allow-list-has-no-stale-entries
  (let [existing (set (map (juxt :method :path) (ra/route-protection router)))]
    (is (empty? (remove existing (keys ra/explicitly-unprotected-routes))))))

(deftest allow-list-entries-are-really-unprotected
  (let [protected (->> (ra/route-protection router)
                       (filter :protected?)
                       (map (juxt :method :path))
                       set)]
    (testing "entries that gained a middleware should leave the allow-list"
      (is (empty? (filter protected (keys ra/explicitly-unprotected-routes)))))))

(deftest admin-routes-are-all-protected
  (is (every? :protected?
              (filter #(.startsWith ^String (:path %) "/api-v2/admin")
                      (ra/route-protection router)))))

(deftest assert-throws-for-unprotected-route
  (let [r (rr/router ["/api-v2/new-thing" {:get {:handler (fn [_] {:status 200})}}])]
    (is (thrown? clojure.lang.ExceptionInfo (ra/assert-routes-protected! r)))))
