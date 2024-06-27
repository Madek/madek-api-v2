(ns madek.api.resources.keywords
  (:require
   [clojure.spec.alpha :as sa]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]
   [madek.api.resources.keywords.keyword :as kw]
   [madek.api.resources.shared.core :as sd]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.helper :refer [convert-map d]]
   [madek.api.utils.pagination :refer [optional-pagination-params pagination-validation-handler swagger-ui-pagination]]

   [next.jdbc :as jdbc]

   [madek.api.resources.coercion-spec2 :as sp ]              ;;ecial-symbol?:refer [::id ::meta_key_id ::term ::description-nil ::position-nil ::external_uris ::external_uri-nil ::rdf_class ::creator_id ::created_at ::updated_at]]


   [reitit.coercion.spec :as spec]
   [spec-tools.core :as st]

   [reitit.coercion.schema]
   [schema.core :as s]



   ))

;### swagger io schema ####################################################################

(def schema_create_keyword
  {:meta_key_id s/Str
   :term s/Str
   (s/optional-key :description) (s/maybe s/Str)
   (s/optional-key :position) (s/maybe s/Int)
   (s/optional-key :external_uris) [s/Str]
   (s/optional-key :rdf_class) s/Str})

(def schema_update_keyword
  {;id
   ;(s/optional-key :meta_key_id) s/Str
   (s/optional-key :term) s/Str
   (s/optional-key :description) (s/maybe s/Str)
   (s/optional-key :position) s/Int
   (s/optional-key :external_uris) [s/Str]
   (s/optional-key :rdf_class) s/Str})

(def schema_export_keyword_usr
  {:id s/Uuid
   :meta_key_id s/Str
   :term s/Str
   :description (s/maybe s/Str)
   :position (s/maybe s/Int)
   :external_uris [s/Any]
   :external_uri (s/maybe s/Str)
   :rdf_class s/Str})

;(def schema_export_keyword_usr
;  {:id s/Uuid
;   :meta_key_id s/Str
;   :term s/Str
;   :description  s/Str
;   :position  s/Int
;   :external_uris [s/Any]
;   :external_uri  s/Str
;   :rdf_class s/Str})

(def schema_export_keyword_adm
  {:id s/Uuid
   :meta_key_id s/Str
   :term s/Str
   :description (s/maybe s/Str)
   :position (s/maybe s/Int)
   :external_uris [s/Any]
   :external_uri (s/maybe s/Str)
   :rdf_class s/Str
   :creator_id (s/maybe s/Uuid)
   :created_at s/Any
   :updated_at s/Any})

(def schema_query_keyword
  {(s/optional-key :id) s/Uuid
   (s/optional-key :meta_key_id) s/Str
   (s/optional-key :term) s/Str
   (s/optional-key :description) s/Str
   (s/optional-key :rdf_class) s/Str})

(defn user-export-keyword [keyword]
  (->
   keyword
   ;(select-keys
   ; [:id :meta_key_id :term :description :external_uris :rdf_class
   ;  :created_at])
   (dissoc :creator_id :created_at :updated_at)
   (assoc                                                   ; support old (singular) version of field
    :external_uri (first (keyword :external_uris)))))

(defn adm-export-keyword [keyword]
  (->
   keyword
   (assoc                                                   ; support old (singular) version of field
    :external_uri (first (keyword :external_uris)))))

;### handlers get and query ####################################################################

(defn handle_adm-get-keyword
  [request]
  (let [keyword (-> request :keyword)]
    (sd/response_ok (adm-export-keyword keyword))))

(defn handle_usr-get-keyword
  [request]
  (let [keyword (-> request :keyword)]
    (sd/response_ok (user-export-keyword keyword))))

(defn handle_usr-query-keywords [request]
  (let [rq (-> request :parameters :query)
        tx (:tx request)
        db-result (kw/db-keywords-query rq tx)
        result (map user-export-keyword db-result)]
    (sd/response_ok {:keywords result})))

(defn handle_adm-query-keywords [request]
  (let [rq (-> request :parameters :query)
        tx (:tx request)
        db-result (kw/db-keywords-query rq tx)
        result (map adm-export-keyword db-result)]
    (sd/response_ok {:keywords result})))

;### handlers write ####################################################################

(defn handle_create-keyword [req]
  (try
    (catcher/with-logging {}
      (let [uid (-> req :authenticated-entity :id)
            data (-> req :parameters :body)
            dwid (assoc data :creator_id uid)
            sql-query (-> (sql/insert-into :keywords)
                          (sql/values [(convert-map dwid)])
                          (sql/returning :*)
                          sql-format)

            ins-result (jdbc/execute-one! (:tx req) sql-query)]
        (if-let [result ins-result]
          (sd/response_ok (adm-export-keyword result))
          (sd/response_failed "Could not create keyword" 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_update-keyword [req]
  (try
    (catcher/with-logging {}
      (let [id (-> req :parameters :path :id)
            data (-> req :parameters :body)
            tx (:tx req)
            sql-query (-> (sql/update :keywords)
                          (sql/set (convert-map data))
                          (sql/where [:= :id id])
                          sql-format)
            upd-res (jdbc/execute-one! tx sql-query)]

        (if (= 1 (:next.jdbc/update-count upd-res))
          ;(sd/response_ok (adm-export-keyword (kw/db-keywords-get-one id)))
          (-> id (kw/db-keywords-get-one tx)
              adm-export-keyword
              sd/response_ok)
          (sd/response_failed "Could not update keyword." 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_delete-keyword [req]
  (try
    (catcher/with-logging {}
      (let [id (-> req :parameters :path :id)
            old-data (-> req :keyword)
            sql-query (-> (sql/delete-from :keywords)
                          (sql/where [:= :id id])
                          sql-format)
            del-res (jdbc/execute-one! (:tx req) sql-query)]

        ; logwrite
        (if (= 1 (::jdbc/update-count del-res))
          (sd/response_ok (adm-export-keyword old-data))
          (sd/response_failed "Could not delete keyword." 406))))
    (catch Exception ex (sd/response_exception ex))))

;### routes ###################################################################

(defn wrap-find-keyword [handler]
  (fn [request] (sd/req-find-data request handler
                  :id
                  :keywords :id
                  :keyword true)))








;(sa/def ::page (st/spec {:spec pos-int?
;                         :description "Page number"
;                         :json-schema/default 1}))
;
;(sa/def ::size (st/spec {:spec pos-int?
;                         :description "Number of items per page"
;                         :json-schema/default 10}))
;
;
;(sa/def ::id (st/spec {:spec uuid?}))
;(sa/def ::meta_key_id (st/spec {:spec string?}))
;(sa/def ::term (st/spec {:spec string?}))
;;(sa/def ::description (st/spec {:spec string?}))
;
;(sa/def ::description
;  (sa/or :nil nil? :string string?))
;
;(sa/def ::position
;  (sa/or :nil nil? :int int?))
;
;(sa/def ::external_uris (st/spec {:spec (sa/coll-of any?)
;                                 :description "An array of any types"}))
;
;(sa/def ::external_uri
;  (sa/or :nil nil? :string string?))
;
;(sa/def ::rdf_class (st/spec {:spec string?}))












;(sa/def ::basic (st/spec {:spec (sa/coll-of any?)
;                                  :description "An array of any types"}))
;
;(def schema_query_pagination2
;  (sa/keys :req-un [::id ::meta_key_id ::term ::description ::position ::external_uris ::external_uri ::rdf_class]))
;



(def schema_query_pagination_only
  (sa/keys
    :opt-un [::sp/page ::sp/size]))

;(def schema_query_pagination
;  (sa/keys
;    :opt-un [::id ::meta_key_id ::term ::description ::rdf_class ::page ::size]))

(def schema_query_keyword
  {(s/optional-key :id) s/Uuid
   (s/optional-key :meta_key_id) s/Str
   (s/optional-key :term) s/Str
   (s/optional-key :description) s/Str
   (s/optional-key :rdf_class) s/Str})




;(sa/def ::response-body (sa/keys :req-un [::keywords]))
;
;(sa/def ::keywords (st/spec {:spec (sa/coll-of ::person)
;                            :description "A list of persons"}))











;(sa/def ::person (sa/keys :req-un [::id ::meta_key_id ::term ::description ::position ::external_uris ::external_uri ::rdf_class]))

(sa/def ::person (sa/keys :req-un [::sp/id ::sp/meta_key_id ::sp/term ::sp/description ::sp/position ::sp/external_uris ::sp/external_uri ::sp/rdf_class]))


(sa/def ::keywords (st/spec {:spec (sa/coll-of ::person)
                             :description "A list of persons"}))

(sa/def ::response-body (sa/keys :req-un [::keywords]))









;; FIXME: broken endpoint to test doc
(def query-routes
  ["/"
   {:swagger {:tags ["keywords"] :security []}}
   ["keywords"
    {:get
     {:summary (sd/sum_pub (d "Query / list keywords."))
      :handler handle_usr-query-keywords

      :coercion spec/coercion
      :parameters {:query schema_query_pagination_only}
      ;:responses {200 {:body (sa/keys :req-un [::keywords])}
      ;:responses {200 {:body {:keywords ::response-body}}
      :responses {200 {:body ::response-body}

                  202 {:description "Successful response, list of items."
                       :schema {}
                       :examples {"application/json" {:message "Here are your items."
                                                      :page 1
                                                      :size 2
                                                      :items [{:id 1, :name "Item 1"}
                                                              {:id 2, :name "Item 2"}]}}}}}}]

   ["keywords/:id"
    {:get
     {:summary (sd/sum_pub "Get keyword for id.")
      :handler handle_usr-get-keyword
      :middleware [wrap-find-keyword]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:id s/Uuid}}
      :responses {200 {:body schema_export_keyword_usr}
                  404 {:body s/Any}}
      :description "Get keyword for id. Returns 404, if no such keyword exists."}}]])

(def admin-routes
  ["/"
   {:swagger {:tags ["admin/keywords"] :security [{"auth" []}]}}
   ["keywords"
    {:get
     {:summary (sd/sum_adm "Query keywords")
      :handler handle_adm-query-keywords
      :coercion reitit.coercion.schema/coercion
      :swagger (swagger-ui-pagination)
      :middleware [wrap-authorize-admin!
                   (pagination-validation-handler (merge optional-pagination-params schema_query_keyword))]
      :parameters {:query schema_query_keyword}
      :responses {200 {:body {:keywords [schema_export_keyword_adm]}}}
      :description "Get keywords id list. TODO query parameters and paging. TODO get full data."}

     :post
     {:summary (sd/sum_adm "Create keyword.")
      :coercion reitit.coercion.schema/coercion
      :handler handle_create-keyword
      :middleware [wrap-authorize-admin!]
      :parameters {:body schema_create_keyword}
      :responses {200 {:body schema_export_keyword_adm}
                  406 {:body s/Any}}}}]
   ["keywords/:id"
    {:get
     {:summary (sd/sum_adm "Get keyword for id")
      :handler handle_adm-get-keyword
      :middleware [wrap-authorize-admin!
                   wrap-find-keyword]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:id s/Uuid}}
      :responses {200 {:body schema_export_keyword_adm}
                  404 {:body s/Any}}
      :description "Get keyword for id. Returns 404, if no such keyword exists."}

     :put
     {:summary (sd/sum_adm "Update keyword.")
      :handler handle_update-keyword
      :middleware [wrap-authorize-admin!
                   wrap-find-keyword]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:id s/Uuid}
                   :body schema_update_keyword}
      :responses {200 {:body schema_export_keyword_adm}
                  404 {:body s/Any}
                  406 {:body s/Any}}}

     :delete
     {:summary (sd/sum_adm "Delete keyword.")
      :handler handle_delete-keyword
      :middleware [wrap-authorize-admin!
                   wrap-find-keyword]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:id s/Uuid}}
      :responses {200 {:body schema_export_keyword_adm}
                  404 {:body s/Any}
                  406 {:body s/Any}}}}]])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
