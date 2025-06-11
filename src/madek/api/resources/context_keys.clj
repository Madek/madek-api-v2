(ns madek.api.resources.context-keys
  (:require
   [clojure.spec.alpha :as sa]
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [logbug.catcher :as catcher]
   [madek.api.resources.shared.core :as sd]
   [madek.api.resources.shared.db_helper :as dbh]
   [madek.api.utils.auth :refer [ADMIN_AUTH_METHODS wrap-authorize-admin!]]
   [madek.api.utils.coercion.spec-alpha-definition :as sp]
   [madek.api.utils.coercion.spec-alpha-definition-map :as sp-map]
   [madek.api.utils.coercion.spec-alpha-definition-nil :as sp-nil]
   [madek.api.utils.helper :refer [cast-to-hstore to-uuid]]
   [madek.api.utils.pagination :refer [pagination-handler]]
   [next.jdbc :as jdbc]
   [reitit.coercion.schema]
   [reitit.coercion.spec :as spec]
   [schema.core :as s]
   [spec-tools.core :as st]
   [taoensso.timbre :refer [error]]))

(defn context_key_transform_ml [context_key]
  (assoc context_key
         :hints (sd/transform_ml (:hints context_key))
         :labels (sd/transform_ml (:labels context_key))
         :descriptions (sd/transform_ml (:descriptions context_key))
         :documentation_urls (sd/transform_ml (:documentation_urls context_key))))

(defn handle_adm-list-context_keys
  [req]
  (let [req-query (-> req :parameters :query)
        db-query (-> (sql/select :*)
                     (sql/from :context_keys)

                     (dbh/build-query-param req-query :id)
                     (dbh/build-query-param req-query :context_id)
                     (dbh/build-query-param req-query :meta_key_id)
                     (dbh/build-query-param req-query :is_required)
                     (dbh/build-query-created-or-updated-after req-query :changed_after)
                     (dbh/build-query-ts-after req-query :created_after "created_at")
                     (dbh/build-query-ts-after req-query :updated_after "updated_at"))
        after-fnc (fn [res] (map context_key_transform_ml res))
        tf (pagination-handler req db-query nil after-fnc)]

    (sd/response_ok tf)))

(defn handle_usr-list-context_keys
  [req]
  (let [req-query (-> req :parameters :query)
        db-query (-> (sql/select :id :context_id :meta_key_id
                                 :is_required :position :length_min :length_max
                                 :labels :hints :descriptions :documentation_urls)
                     (sql/from :context_keys)
                     (dbh/build-query-param req-query :id)
                     (dbh/build-query-param req-query :context_id)
                     (dbh/build-query-param req-query :meta_key_id)
                     (dbh/build-query-param req-query :is_required))
        after-fnc (fn [res] (map context_key_transform_ml res))
        tf (pagination-handler req db-query nil after-fnc)]

    ;(info "handle_usr-list-context_keys" "\ndb-query\n" db-query)
    (sd/response_ok tf)))

(defn handle_adm-get-context_key
  [req]
  (let [result (-> req :context_key context_key_transform_ml)]
    ;(info "handle_get-context_key: result: " result)
    (sd/response_ok result)))

(defn handle_usr-get-context_key
  [req]
  (let [context_key (-> req :context_key context_key_transform_ml)
        result (dissoc context_key :admin_comment :updated_at :created_at)]
    ;(info "handle_usr-get-context_key" "\nbefore\n" context_key "\nresult\n" result)
    (sd/response_ok result)))

(defn handle_create-context_keys
  [req]
  (try
    (catcher/with-logging {}
      (let [data (-> req :parameters :body)
            sql (-> (sql/insert-into :context_keys)
                    (sql/values [(cast-to-hstore data)])
                    (sql/returning :*)
                    sql-format)
            ins-res (jdbc/execute-one! (:tx req) sql)]

        (if-let [result ins-res]
          (sd/response_ok (context_key_transform_ml result))
          (sd/response_failed "Could not create context_key." 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_update-context_keys
  [req]
  (try
    (catcher/with-logging {}
      (let [data (-> req :parameters :body)
            id (to-uuid (-> req :parameters :path :id))
            dwid (assoc data :id id)
            tx (:tx req)
            query (-> (sql/update :context_keys)
                      (sql/set (cast-to-hstore dwid))
                      (sql/where [:= :id id])
                      sql-format)
            upd-result (jdbc/execute-one! tx query)]

        (sd/logwrite req (str "handle_update-context_keys: " id "\nnew-data\n" dwid "\nupd-result: " upd-result))

        (if (= 1 (::jdbc/update-count upd-result))
          (sd/response_ok (context_key_transform_ml (dbh/query-eq-find-one :context_keys :id id tx)))
          (sd/response_failed "Could not update context_key." 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_delete-context_key
  [req]
  (try
    (catcher/with-logging {}
      (let [context_key (-> req :context_key)
            id (-> req :context_key :id)
            sql (-> (sql/delete-from :context_keys)
                    (sql/where [:= :id id])
                    sql-format)
            del-result (jdbc/execute-one! (:tx req) sql)]

        (sd/logwrite req (str "handle_delete-context_key: " id " result: " del-result))
        (if (= 1 (::jdbc/update-count del-result))
          (sd/response_ok (context_key_transform_ml context_key))
          (error "Could not delete context_key: " id))))
    (catch Exception ex (sd/response_exception ex))))

(defn wwrap-find-context_key [param colname send404]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler
                                    param
                                    :context_keys colname
                                    :context_key send404))))

(def schema_import_context_keys
  {;:id s/Str
   :context_id s/Str
   :meta_key_id s/Str
   :is_required s/Bool
   (s/optional-key :length_max) (s/maybe s/Int)
   (s/optional-key :length_min) (s/maybe s/Int)
   :position s/Int
   (s/optional-key :admin_comment) (s/maybe s/Str)
   ; hstore
   (s/optional-key :labels) (s/maybe sd/schema_ml_list)
   (s/optional-key :descriptions) (s/maybe sd/schema_ml_list)
   (s/optional-key :hints) (s/maybe sd/schema_ml_list)
   (s/optional-key :documentation_urls) (s/maybe sd/schema_ml_list)})

(def schema_update_context_keys
  {;(s/optional-key :id) s/Str
   ;:context_id s/Str
   ;(s/optional-key :meta_key_id) s/Str
   (s/optional-key :is_required) s/Bool
   (s/optional-key :length_max) (s/maybe s/Int)
   (s/optional-key :length_min) (s/maybe s/Int)
   (s/optional-key :position) s/Int
   (s/optional-key :admin_comment) (s/maybe s/Str)
   (s/optional-key :labels) (s/maybe sd/schema_ml_list)
   (s/optional-key :descriptions) (s/maybe sd/schema_ml_list)
   (s/optional-key :hints) (s/maybe sd/schema_ml_list)
   (s/optional-key :documentation_urls) (s/maybe sd/schema_ml_list)})

(def schema_export_context_key
  {:id s/Uuid
   :context_id s/Str
   :meta_key_id s/Str
   :is_required s/Bool
   :length_max (s/maybe s/Int)
   :length_min (s/maybe s/Int)
   :position s/Int

   :labels (s/maybe sd/schema_ml_list)
   :descriptions (s/maybe sd/schema_ml_list)
   :hints (s/maybe sd/schema_ml_list)

   :documentation_urls (s/maybe sd/schema_ml_list)})

(def schema_export_context_key_paginated
  {:data [schema_export_context_key]
   :pagination {:total_rows s/Int
                :total_pages s/Int
                :page s/Int
                :size s/Int}})

(sa/def :adm/context-keys (sa/keys :opt-un [::sp/id ::sp/meta_key_id ::sp/context_id ::sp/is_required ::sp/changed_after
                                            ::sp/created_after ::sp/updated_after
                                            ::sp/page ::sp/size]))

(def schema_export_context_key_admin
  {:id s/Uuid
   :context_id s/Str
   :meta_key_id s/Str
   :is_required s/Bool
   :length_max (s/maybe s/Int)
   :length_min (s/maybe s/Int)
   :position s/Int

   :labels (s/maybe sd/schema_ml_list)
   :descriptions (s/maybe sd/schema_ml_list)
   :hints (s/maybe sd/schema_ml_list)

   :documentation_urls (s/maybe sd/schema_ml_list)

   :admin_comment (s/maybe s/Str)
   :updated_at s/Any
   :created_at s/Any})

(sa/def :adm/context-key-response (sa/keys
                                   :req-un [::sp/id ::sp/context_id ::sp/meta_key_id
                                            ::sp/is_required ::sp-nil/length_max ::sp-nil/length_min ::sp/position
                                            ::sp-map/labels ::sp-map/descriptions ::sp-map/hints
                                            ::sp-map/documentation_urls
                                            ::sp-nil/admin_comment
                                            ::sp/updated_at ::sp/created_at]))

(sa/def :adm/context-keys-response (st/spec {:spec (sa/coll-of :adm/context-key-response)
                                             :description "A list of context-keys"}))

(sa/def :adm/context-keys-response-paginated
  (st/spec
   {:spec (sa/keys :req-un {::sp/data ::sp/pagination})
    :description "Paginated list of full_texts"}))

(sa/def :adm/context-keys-response-combined
  (st/spec
   {:spec (sa/or :flat :adm/context-keys-response
                 :paginated :adm/context-keys-response-paginated)
    :description "Supports both flat and paginated full_texts formats"}))

; TODO docu
; TODO tests
(def admin-routes
  ["/"
   {:openapi {:tags ["admin/context-keys"] :security ADMIN_AUTH_METHODS}}
   ["context-keys"
    {:post
     {:summary (sd/sum_adm "Post context_key by id.")
      :swagger {:security [{"auth" []}]}
      :handler handle_create-context_keys
      :middleware [wrap-authorize-admin!]
      :content-type "application/json"
      :accept "application/json"
      :coercion reitit.coercion.schema/coercion
      :parameters {:body schema_import_context_keys}
      :responses {200 {:description "Returns the created context_key."
                       :body schema_export_context_key_admin}
                  406 {:description "Could not create context_key."
                       :body s/Any}}}

     ; context_key list / query
     :get
     {:summary (sd/sum_adm "Query context_keys.")
      :handler handle_adm-list-context_keys
      :middleware [wrap-authorize-admin!]
      :parameters {:query :adm/context-keys}
      :coercion spec/coercion
      :responses {200 {:description "Returns the context_keys."
                       :body :adm/context-keys-response-combined}
                  406 {:description "Could not list context_keys."
                       :body any?}}}}]
   ; edit context_key
   ["context-keys/:id"
    {:get
     {:summary (sd/sum_adm "Get context_key by id.")
      :handler handle_adm-get-context_key
      :middleware [wrap-authorize-admin!
                   (wwrap-find-context_key :id :id true)]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:id s/Uuid}}
      :responses {200 {:description "Returns the context_key."
                       :body schema_export_context_key_admin}
                  404 {:description "Not found"
                       :body s/Any}
                  406 {:description "Could not get context_key."
                       :body s/Any}}}

     :put
     {:summary (sd/sum_adm "Update context_keys with id.")
      :handler handle_update-context_keys
      :middleware [wrap-authorize-admin!
                   (wwrap-find-context_key :id :id true)]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:id s/Uuid}
                   :body schema_update_context_keys}
      :responses {200 {:description "Returns the updated context_key."
                       :body schema_export_context_key_admin}
                  404 {:description "Not found"
                       :body s/Any}
                  406 {:description "Could not update context_key."
                       :body s/Any}}}

     :delete
     {:summary (sd/sum_adm_todo "Delete context_key by id.")
      :coercion reitit.coercion.schema/coercion
      :handler handle_delete-context_key
      :middleware [wrap-authorize-admin!
                   (wwrap-find-context_key :id :id true)]
      :parameters {:path {:id s/Uuid}}
      :responses {200 {:description "Returns the deleted context_key."
                       :body schema_export_context_key_admin}
                  404 {:description "Not found"
                       :body s/Any}
                  406 {:description "Could not delete context_key."
                       :body s/Any}}}}]])

; TODO docu
(def user-routes
  ["/"
   {:openapi {:tags ["context-keys"] :security []}}
   ["context-keys"
    {:get
     {:summary (sd/?no-auth? (sd/sum_pub "Query / List context_keys."))
      :handler handle_usr-list-context_keys
      :coercion reitit.coercion.schema/coercion
      :parameters {:query {(s/optional-key :id) s/Uuid
                           (s/optional-key :context_id) s/Str
                           (s/optional-key :meta_key_id) s/Str
                           (s/optional-key :page) s/Int
                           (s/optional-key :size) s/Int
                           (s/optional-key :is_required) s/Bool}}
      :responses {200 {:description "Returns the context_keys."
                       :body (s/->Either [[schema_export_context_key] schema_export_context_key_paginated])}
                  403 (sd/create-error-message-response "Forbidden." "The token has no admin-privileges.")
                  406 {:description "Could not list context_keys."
                       :body s/Any}}}}]

   ["context-keys/:id"
    {:get
     {:summary (sd/?no-auth? (sd/sum_pub "Get context_key by id."))
      :handler handle_usr-get-context_key
      :middleware [(wwrap-find-context_key :id :id true)]
      :coercion reitit.coercion.schema/coercion
      :parameters {:path {:id s/Uuid}}
      :responses {200 {:description "Returns the context_key."
                       :body schema_export_context_key}
                  400 {:description "Bad request"
                       :body s/Any}
                  404 {:description "Not found"
                       :body s/Any}}}}]])
