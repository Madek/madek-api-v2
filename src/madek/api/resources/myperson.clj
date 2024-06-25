;(ns clojure-api.core
(ns madek.api.resources.myperson
  (:require
   [clojure.spec.alpha :as s]
   [reitit.coercion.spec]

   ;[schema.core :as ss]
   [spec-tools.core :as st])
  (:import (java.util UUID)))

; Define specs for person data with descriptions and default values
(s/def ::uuid (st/spec {:spec uuid?
                        :description "A unique identifier for the person"
                        :json-schema/default (str (UUID/randomUUID))}))

(s/def ::firstname (st/spec {:spec string?
                             :description "The first name of the person"}))

(s/def ::lastname (st/spec {:spec string?
                            :description "The last name of the person"}))

(s/def ::age (st/spec {:spec pos-int?
                       :description "The age of the person"
                       :json-schema/default 0}))

(s/def ::person (s/keys :req-un [::uuid ::firstname ::lastname ::age]))
(s/def ::person-list (s/coll-of ::person))

;; Define specs for pagination
(s/def ::page (st/spec {:spec pos-int?
                        :description "Page number"
                        :json-schema/default 1}))

(s/def ::size (st/spec {:spec pos-int?
                        :description "Number of items per page"
                        :json-schema/default 10}))

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

(def person-routes
  ["test/person"
   {:tags ["test/person"]}

   ["/all"
    {:get {:summary "Get all persons with pagination"
           :parameters {:query {:page ::page :size ::size}}
           :responses {200 {:body ::person-list}}
           :coercion reitit.coercion.spec/coercion
           :handler (fn [{{{:keys [page size]} :query} :parameters}]
                      (let [paginated-data (paginate @person-db page size)]
                        {:status 200
                         :body paginated-data}))}}]

   ["/"
    {:post {:summary "Add a new person"
            :parameters {:body ::person}
            :responses {201 {:body ::person}}
            :coercion reitit.coercion.spec/coercion
            :handler (fn [{{:keys [body]} :parameters}]
                       (add-person body)
                       {:status 201
                        :body body})}}]

   ["/{id}"
    {:get {:summary "Get a person by ID"
           :parameters {:path {:id ::uuid}}
           :responses {200 {:body ::person}}
           :coercion reitit.coercion.spec/coercion

           :handler (fn [{{:keys [path]} :parameters}]
                      (if-let [person (get-person (:id path))]
                        {:status 200
                         :body person}
                        {:status 404
                         :body {:error "Person not found"}}))}

     :put {:summary "Update a person by ID"
           :parameters {:path {:id ::uuid}
                        :body ::person}
           :responses {200 {:body ::person}}
           :coercion reitit.coercion.spec/coercion
           :handler (fn [{{:keys [path body]} :parameters}]
                      (update-person (:id path) body)
                      {:status 200
                       :body body})}

     :delete {:summary "Delete a person by ID"
              :parameters {:path {:id ::uuid}}
              :coercion reitit.coercion.spec/coercion
              :responses {204 {:description "No Content"}}
              :handler (fn [{{:keys [path]} :parameters}]
                         (delete-person (:id path))
                         {:status 204})}}]])
