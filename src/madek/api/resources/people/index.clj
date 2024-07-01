(ns madek.api.resources.people.index
  (:require
   [clojure.spec.alpha :as sa]
   [cuerdas.core :refer [empty-or-nil?]]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.resources.people.common :as common]

   [madek.api.resources.people.get :as get-person]
   [madek.api.resources.shared.core :as sd]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.coercion.spec-alpha-definition :as sp]
   [madek.api.utils.helper :refer [parse-specific-keys]]
   [madek.api.pagination :as pagination]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]

   [reitit.coercion.spec :as spec]
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
      ;(pagination/sql-offset-and-limit query-params)
      (pagination/add-offset-for-honeysql query-params)
      (filter-query query-params)))

(defn handler
  "Get an index of the people. Query parameters are pending to be implemented."
  ;[{{query :query} :parameters params :params tx :tx :as req}]
  [{{query :query} :parameters tx :tx :as req}]
  (debug 'query query)
  (let [
        ;_ (doseq [k (keys params)]
        ;    ;; print count of params
        ;
        ;    (println "Count of map keys:" (count (keys params)))
        ;
        ;    (println (str k " " (type (get params k))))
        ;    )


        p (println ">o> >> -------- already casted params" )
        _ (doseq [k (keys query)]

            (println "Count of map keys:" (count (keys query)))

            (println (str k " " (type (get query k))))
            )

        ;defaults {:page 0 :count 1000}
        ;params (parse-specific-keys params defaults)
        query (-> (build-query query)
                  ;(pagination/sql-offset-and-limit params)
                  (pagination/add-offset-for-honeysql query)
                  sql-format)
        people (jdbc/execute! tx query)]
    (debug 'people people)
    {:status 200, :body {:people people}}))

(sa/def ::people-query-def (sa/keys :opt-un [::sp/institution ::sp/subtype ::sp/page ::sp/size]))

(def query-schema
  {(s/optional-key :institution) s/Str
   (s/optional-key :subtype) s/Str})

(def route
  {:summary (sd/sum_adm "Get list of people ids.")
   :description "Get list of people ids."
   :handler handler

   ;:parameters {:query query-schema}
   :parameters {:query ::people-query-def}
   :middleware [wrap-authorize-admin!
                ;(pagination-validation-handler (merge optional-pagination-params query-schema))
                ]
   ;:swagger (swagger-ui-pagination)
   ;:coercion reitit.coercion.schema/coercion
   ;:responses {200 {:body {:people [get-person/schema]}}}

   ;:responses {200 {:body {:people [get-person/schema]}}}
   :responses {200 {:body ::get-person/response-people-body}}

   :coercion spec/coercion})

;### Debug ####################################################################
;(debug/debug-ns *ns*)
