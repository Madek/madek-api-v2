(ns madek.api.resources.keywords
  (:require
   [clojure.spec.alpha :as sa]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]
   [madek.api.resources.keywords.keyword :as kw]
   [madek.api.resources.shared.core :as sd]
   [madek.api.utils.auth :refer [ADMIN_AUTH_METHODS]]
   [madek.api.utils.auth :refer [wrap-authorize-admin!]]
   [madek.api.utils.coercion.spec-alpha-definition :as sp]
   [madek.api.utils.coercion.spec-alpha-definition-nil :as sp-nil]
   [madek.api.utils.helper :refer [convert-map d]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [reitit.coercion.spec :as spec]
   [schema.core :as s]
   [spec-tools.core :as st]))

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

(defn user-export-keyword [keyword]
  (->
   keyword
   ;(select-keys
   ; [:id :meta_key_id :term :description :external_uris :rdf_class
   ;  :created_at])
   (dissoc :creator_id :created_at :updated_at)
   (assoc ; support old (singular) version of field
    :external_uri (first (keyword :external_uris)))))

(defn adm-export-keyword [keyword]
  (->
   keyword
   (assoc ; support old (singular) version of field
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

(sa/def ::person-opt (sa/keys :opt-un [::sp/id ::sp/meta_key_id ::sp/term ::sp/description ::sp/rdf_class]))

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

(sa/def :usr/person (sa/keys :req-un [::sp/id ::sp/meta_key_id ::sp/term ::sp-nil/description ::sp-nil/position ::sp/external_uris ::sp-nil/external_uri ::sp/rdf_class]))
(sa/def :usr/keywords (st/spec {:spec (sa/coll-of :usr/person)
                                :description "A list of persons"}))
(sa/def ::response-body (sa/keys :req-un [:usr/keywords]))

(sa/def :adm/person-admin (sa/keys :req-un [::sp/id ::sp/meta_key_id ::sp/term ::sp-nil/description ::sp-nil/position ::sp/external_uris ::sp-nil/external_uri ::sp/rdf_class ::sp/creator_id ::sp/created_at ::sp/updated_at]))

(sa/def :adm/keywords (st/spec {:spec (sa/coll-of :adm/person-admin)
                                :description "A list of persons"}))
(sa/def ::response-body-adm (sa/keys :req-un [:adm/keywords]))

;; FIXME: broken endpoint to test doc
(def query-routes
  ["/"
   {:openapi {:tags ["keywords"] :security []}}
   ["keywords"
    {:get
     {:summary (sd/sum_pub (d "Query / list keywords."))
      :handler handle_usr-query-keywords
      :coercion spec/coercion
      :parameters {:query sp/schema_pagination_opt}
      :responses {200 {:description "Successful response, list of items."
                       :body ::response-body}
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
      :responses {200 {:description "Returns the keyword."
                       :body schema_export_keyword_usr}
                  404 {:description "Keyword not found."
                       :body s/Any}}
      :description "Get keyword for id. Returns 404, if no such keyword exists."}}]])

(def admin-routes
  ["/"
   {:openapi {:tags ["admin/keywords"] :security ADMIN_AUTH_METHODS}}
   ["keywords"
    {:get
     {:summary (sd/sum_adm "Query keywords")
      :handler handle_adm-query-keywords
      :coercion spec/coercion
      :middleware [wrap-authorize-admin!]
      :parameters {:query ::person-opt}
      :responses {200 {:description "Returns the list of keywords."
                       :body ::response-body-adm}}
      :description "Get keywords id list. TODO query parameters and paging. TODO get full data."}

     :post
     {:summary (sd/sum_adm "Create keyword.")
      :coercion reitit.coercion.schema/coercion
      :handler handle_create-keyword
      :middleware [wrap-authorize-admin!]
      :parameters {:body schema_create_keyword}
      :responses {200 {:description "Returns the created keyword."
                       :body schema_export_keyword_adm}
                  406 {:description "Could not create keyword."
                       :body s/Any}}}}]
   ["keywords/:id"
    {:get
     {:summary (sd/sum_adm "Get keyword for id")
      :handler handle_adm-get-keyword
      :middleware [wrap-authorize-admin!
                   wrap-find-keyword]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:id s/Uuid}}
      :responses {200 {:description "Returns the keyword."
                       :body schema_export_keyword_adm}
                  404 {:description "Keyword not found."
                       :body s/Any}}
      :description "Get keyword for id. Returns 404, if no such keyword exists."}

     :put
     {:summary (sd/sum_adm "Update keyword.")
      :handler handle_update-keyword
      :middleware [wrap-authorize-admin!
                   wrap-find-keyword]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:id s/Uuid}
                   :body schema_update_keyword}
      :responses {200 {:description "Returns the updated keyword."
                       :body schema_export_keyword_adm}
                  404 {:description "Keyword not found."
                       :body s/Any}
                  406 {:description "Could not update keyword."
                       :body s/Any}}}

     :delete
     {:summary (sd/sum_adm "Delete keyword.")
      :handler handle_delete-keyword
      :middleware [wrap-authorize-admin!
                   wrap-find-keyword]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:id s/Uuid}}
      :responses {200 {:description "Returns the deleted keyword."
                       :body schema_export_keyword_adm}
                  404 {:description "Keyword not found."
                       :body s/Any}
                  406 {:description "Could not delete keyword."
                       :body s/Any}}}}]])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
