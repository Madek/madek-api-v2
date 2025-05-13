(ns madek.api.resources.people.index
  (:require
   [clojure.spec.alpha :as sa]
   [cuerdas.core :refer [empty-or-nil?]]
   [honey.sql.helpers :as sql]
   [madek.api.resources.people.common :as common]
   [madek.api.resources.people.get :as get-person]
   [madek.api.resources.shared.core :as sd]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.coercion.spec-alpha-definition :as sp]
   [madek.api.utils.pagination :refer [pagination-handler]]
   [reitit.coercion.schema]
   [reitit.coercion.spec :as spec]
   [taoensso.timbre :refer [debug]]))

(defn subtype-filter [query {subtype :subtype}]
  (if (empty-or-nil? subtype)
    query
    (sql/where query [:= :people.subtype subtype])))

(defn institution-filer [query {institution :institution}]
  (if (empty-or-nil? institution)
    query
    (sql/where query [:= :people.institution institution])))

(defn institutional-id-filter [query {institutional_id :institutional_id}]
  (if (empty-or-nil? institutional_id)
    query
    (sql/where query [:= :people.institutional_id institutional_id])))

(defn search [query {term :search-term}]
  (if (empty-or-nil? term)
    query
    (-> query
        (dissoc :offset :limit)
        (sql/select [[:word_similarity term :searchable] :term-similarity])
        (update-in [:order-by] #(into [[:term-similarity :desc]] %))
        (sql/limit 15)
        (sql/where [:> [:word_similarity term :searchable] 0]))))

(defn filter-query [sql-query query-params]
  (-> sql-query
      (subtype-filter query-params)
      (institution-filer query-params)
      (institutional-id-filter query-params)
      (search query-params)))

(defn build-query [query-params]
  (-> common/base-query
      (sql/order-by [:people.last_name :asc]
                    [:people.first_name :asc]
                    [:people.id :asc])
      (filter-query query-params)))

(defn handler
  "Get an index of the people. Query parameters are pending to be implemented."
  [{{params :query} :parameters tx :tx :as req}]
  (debug 'params params)
  (let [query (build-query params)
        result (pagination-handler req query :people)]
    (debug 'people result)
    {:status 200, :body result}))

(sa/def ::people-query-def
  (sa/keys :opt-un [::sp/institution ::sp/institutional_id ::sp/subtype ::sp/page ::sp/size]))

(def route
  {:summary (sd/sum_adm "Get list of people ids.")
   :description "Get list of people ids."
   :handler handler
   :parameters {:query ::people-query-def}
   :middleware [wrap-authorize-admin!]
   :responses {200 {:description "List of people ids."
                    :body ::get-person/response-people-body}}
   :coercion spec/coercion})

;### Debug ####################################################################
;(debug/debug-ns *ns*)
