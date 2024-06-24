(ns madek.api.resources.test
  (:require [clojure.spec.alpha :as s]
            [spec-tools.core :as st]
            [reitit.coercion.spec])
  (:import (java.util UUID)))

;; TODO: mr/swagger-ui-by-spec-alpha
;; Define specs for person data with descriptions and default values
(s/def ::uuid (st/spec {:spec uuid?
                        :description "A unique identifier for the person"
                        :json-schema/default (str (UUID/randomUUID))}))

(s/def ::firstname (st/spec {:spec string?
                             :description "The first name of the person"}))

(s/def ::lastname (st/spec {:spec string?
                            :description "The last name of the person"}))

(s/def ::age (st/spec {:spec pos-int?
                       :description "The age of the person"
                       :json-schema/default 99}))

(s/def ::person (s/keys :req-un [::uuid ::firstname ::lastname ::age]))
(s/def ::person-list (s/coll-of ::person))

;; Define specs for pagination
(s/def ::page (st/spec {:spec pos-int?
                        :description "Page number"
                        :json-schema/default 1}))

(s/def ::size (st/spec {:spec pos-int?
                        :require false
                        :description "Number of items per page"
                        :json-schema/default 10}))



;; Define specs for person data with descriptions and default values
(s/def ::uuid-or-null (s/nilable uuid?))
(s/def ::string-or-null (s/nilable string?))

;; Example attribute that can be either a UUID or null
(s/def ::identifier (st/spec {:spec ::uuid-or-null
                              :description "A unique identifier for the person, can be UUID or null"}))

;; Example attribute that can be either a string or null
(s/def ::nickname (st/spec {:spec ::string-or-null
                            :description "The nickname of the person, can be string or null"}))


;; Define specs for required/optional query parameters
(s/def ::person (s/keys :req-un [::uuid ::age ::firstname] :opt-un [::lastname ::age ::identifier ::nickname]))


;; In-memory database for person data
(def person-db (atom []))

;; Helper functions
(defn add-person [person]
  (swap! person-db conj person))

(defn get-person [id]
  (some #(when (= (:uuid %) id) %) @person-db))

(defn update-person [id person]
  (swap! person-db #(mapv (fn [p] (if (= (:uuid p) id) person p)) %)))

(defn delete-person [id]
  (swap! person-db (fn [persons] (remove #(= (:uuid %) id) persons))))

(defn paginate [data page size]
  (let [start (* (dec page) size)
        end (min (+ start size) (count data))]
    (subvec data start end)))

(def test
  [
   ["/test"
    {:tags ["test"]}

    ["/all"
     {:get {:summary "Get all persons with pagination"

            :parameters {:query ::person}

            :responses {200 {:body ::person-list}}
            :handler (fn [{{{:keys [page size]} :query} :parameters}]
                       (let [paginated-data (paginate @person-db page size)]
                         {:status 200
                          :body paginated-data}))}}]

    ["/"
     {:post {:summary "Add a new person"
             :parameters {:body ::person}
             :responses {201 {:body ::person}}
             :handler (fn [{{:keys [body]} :parameters}]
                        (add-person body)
                        {:status 201
                         :body body})}}]
    ]]
  )
