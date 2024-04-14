(ns madek.api.resources.people.index
  (:require
   [cuerdas.core :refer [empty-or-nil?]]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.db.core :refer [get-ds]]
   [madek.api.resources.people.common :as common]
   [madek.api.resources.people.get :as get-person]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.helper :refer [parse-specific-keys t]]
   [madek.api.utils.pagination :as pagination]
   [madek.api.utils.validation :refer [greater-equal-zero-validation greater-zero-validation]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [schema.core :as s]
   [taoensso.timbre :refer [debug]]))

(defn subtype-filter [query {subtype :subtype}]
  (if (empty-or-nil? subtype)
    query
    (sql/where query [:= :people.subtype subtype])))

(defn institution-filer [query {institution :institution}]
  (if (empty-or-nil? institution)
    query
    (sql/where query [:= :people.institution institution])))

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
      (search query-params)))

(defn build-query [query-params]
  (-> common/base-query
      (sql/order-by [:people.last_name :asc]
                    [:people.first_name :asc]
                    [:people.id :asc])
      (pagination/sql-offset-and-limit query-params)
      (filter-query query-params)))

(comment
  (-> (build-query {:search-term "Schänk Thomas"})
      spy
      (sql-format :inline true)
      (->> (jdbc/execute! (get-ds)))))

(defn handler
  "Get an index of the people. Query parameters are pending to be implemented."
  [{{query :query} :parameters params :params tx :tx :as req}]
  (debug 'query query)
  (let [defaults {:page 0 :count 1000}
        params (parse-specific-keys params defaults)
        query (-> (build-query query)
                  (pagination/sql-offset-and-limit params)
                  sql-format)
        people (jdbc/execute! tx query)]
    (debug 'people people)
    {:status 200, :body {:people people}}))

(def query-schema
  {(s/optional-key :institution) s/Str
   (s/optional-key :subtype) s/Str
   (s/optional-key :page) greater-equal-zero-validation
   (s/optional-key :count) greater-zero-validation})

(def route
  {:summary (sd/sum_adm "Get list of people ids.")
   :description "Get list of people ids."
   :swagger {:produces "application/json"}
   :parameters {:query query-schema}
   :content-type "application/json"
   :handler handler
   :middleware [wrap-authorize-admin!]
   :coercion reitit.coercion.schema/coercion
   :responses {200 {:body {:people [get-person/schema]}}}})

;### Debug ####################################################################
;(debug/debug-ns *ns*)
