(ns madek.api.resources.full-texts
  (:require [clojure.spec.alpha :as sa]
            [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]
            [logbug.catcher :as catcher]
            [madek.api.pagination :as pagination]
            [madek.api.resources.shared.core :as sd]
            [madek.api.resources.shared.db_helper :as dbh]
            [madek.api.resources.shared.json_query_param_helper :as jqh]
            [madek.api.utils.auth :refer [ADMIN_AUTH_METHODS]]
            [madek.api.utils.auth :refer [wrap-authorize-admin!]]
            [madek.api.utils.coercion.spec-alpha-definition :as sp]
            [next.jdbc :as jdbc]
            [reitit.coercion.schema]
            [reitit.coercion.spec :as spec]
            [schema.core :as s]
            [spec-tools.core :as st]
            [taoensso.timbre :refer [error info]]))

(defn handle_list-full_texts
  [req]
  (let [query-params (-> req :parameters :query)
        full_data (:full_data query-params)
        base-query (if (true? full_data)
                     (sql/select :*)
                     (sql/select :media_resource_id))
        db-query (-> base-query
                     (sql/from :full_texts)
                     (dbh/build-query-param query-params :media_resource_id)
                     (dbh/build-query-param-like query-params :text)
                     (pagination/sql-offset-and-limit query-params)
                     sql-format)
        db-result (jdbc/execute! (:tx req) db-query)]

    (info "handle_list-full_texts:" "\nquery:\n" db-query)
    (sd/response_ok db-result)))

(defn handle_get-full_text
  [req]
  (let [ft (-> req :full_text)]
    (sd/response_ok ft)))

(defn handle_create-full_texts
  [req]
  (try
    (catcher/with-logging {}
      (let [rdata (-> req :parameters :body)
            mr-id (or (:media_resource_id rdata)
                      (-> req :parameters :path :media_resource_id)
                      (-> req :parameters :path :collection_id)
                      (-> req :parameters :path :media_entry_id))
            ins-data (assoc rdata :media_resource_id mr-id)
            sql-query (-> (sql/insert-into :full_texts)
                          (sql/values [ins-data])
                          (sql/returning :*)
                          sql-format)
            ins-res (jdbc/execute-one! (:tx req) sql-query)]

        (info "handle_create-full_texts: " "\nnew-data:\n" ins-data "\nresult:\n" ins-res)

        (if ins-res
          (sd/response_ok ins-res)
          (sd/response_failed "Could not create full_text." 406))))
    (catch Exception e (sd/response_exception e))))

(defn handle_update-full_texts
  [req]
  (try
    (catcher/with-logging {}
      (let [data (-> req :parameters :body)
            mr-id (or (:media_resource_id data)
                      (-> req :parameters :path :media_resource_id)
                      (-> req :parameters :path :collection_id)
                      (-> req :parameters :path :media_entry_id))
            dwid (assoc data :media_resource_id mr-id)
            sql-query (-> (sql/update :full_texts)
                          (sql/set dwid)
                          (sql/where [:= :media_resource_id mr-id])
                          (sql/returning :*)
                          sql-format)
            upd-result (jdbc/execute-one! (:tx req) sql-query)]

        (info "handle_update-full_texts: " mr-id "\new-data:\n" dwid "\nresult:\n" upd-result)

        (if upd-result
          (sd/response_ok upd-result)
          (sd/response_failed "Could not update full_text." 406))))
    (catch Exception e (sd/response_exception e))))

(defn handle_delete-full_texts
  [req]
  (try
    (catcher/with-logging {}
      (let [full-text (-> req :full_text)
            mr-id (:media_resource_id full-text)
            sql-query (-> (sql/delete-from :full_texts)
                          (sql/where [:= :media_resource_id mr-id])
                          (sql/returning :*)
                          sql-format)
            del-result (jdbc/execute-one! (:tx req) sql-query)]

        (info "handle_delete-full_texts: " mr-id " result: " del-result)

        (if del-result
          (sd/response_ok del-result)
          (error "Could not delete full_text " mr-id))))
    (catch Exception e (sd/response_exception e))))

(defn wrap-find-full_text [param send404]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param
                                    :full_texts :media_resource_id
                                    :full_text send404))))

(sa/def :ft-query/schema-query-def (sa/keys :opt-un [::sp/full_data ::sp/media_resource_id ::sp/text ::sp/page ::sp/size]))

(sa/def ::response-schema-def (sa/keys :req-un [::sp/media_resource_id] :opt-un [::sp/text]))

(sa/def :usr-list/full-texts (st/spec {:spec (sa/coll-of ::response-schema-def)
                                       :description "A list of full_texts"}))

; TODO tests
; TODO howto access control or full_texts is public meta data
(def query-routes
  ["/"
   {:openapi {:tags ["full_texts"]}}
   ["full_texts"
    {:get {:summary (sd/sum_usr "Query or list full_texts.")
           :handler handle_list-full_texts
           :coercion spec/coercion
           :responses {200 {:description "Returns the full_texts."
                            :body :usr-list/full-texts}}
           :parameters {:query :ft-query/schema-query-def}}}]

   ["full_texts/:media_resource_id"
    {:get {:summary (sd/sum_usr "Get full_text.")
           :handler handle_get-full_text
           :coercion reitit.coercion.schema/coercion
           :responses {200 {:description "Returns the full_text."
                            :body s/Any}}
           :parameters {:path {:media_resource_id s/Uuid}}
           :middleware [(wrap-find-full_text :media_resource_id true)]}}]])

; TODO tests
; TODO Frage: siehe web-app: ??
; wer darf full-texts anlegen?
; nur der admin?

(def edit-routes
  [["/full_text"
    {:openapi {:tags ["admin/full_text"] :security ADMIN_AUTH_METHODS}}
    ["/"
     {:post {:summary (sd/sum_adm "Create full_texts entry????")
             :swagger {:consumes "application/json" :produces "application/json"}
             :handler handle_create-full_texts
             :coercion reitit.coercion.spec/coercion
             :parameters {:body {:text string?
                                 :media_resource_id uuid?}}
             :middleware [wrap-authorize-admin!]
             :responses {200 {:description "Returns the created full_text."
                              :body ::response-schema-def}
                         406 {:description "Creation failed."
                              :schema string?
                              :examples {"application/json" {:message "Could not create full_text."}}}}}}]

    ["/full_text/:media_resource_id"
     {:post {:summary (sd/sum_adm "Create full_texts entry")
             :swagger {:consumes "application/json" :produces "application/json"}
             :handler handle_create-full_texts
             :coercion reitit.coercion.spec/coercion
             :parameters {:path {:media_resource_id uuid?}
                          :body {:text string?}}
             :responses {200 {:description "Returns the created full_text."
                              :body ::response-schema-def}
                         406 {:description "Creation failed."
                              :schema string?
                              :examples {"application/json" {:message "Could not create full_text."}}}}
             :middleware [wrap-authorize-admin!]}
      :put {:summary (sd/sum_adm "Update full_text.")
            :coercion reitit.coercion.spec/coercion
            :parameters {:path {:media_resource_id uuid?}
                         :body {:text string?}}
            :middleware [wrap-authorize-admin!
                         (wrap-find-full_text :media_resource_id true)]
            :responses {200 {:description "Returns the updated full_text."
                             :body ::response-schema-def}
                        406 {:description "Update failed."
                             :schema string?
                             :examples {"application/json" {:message "Could not update full_text."}}}}
            :handler handle_update-full_texts}

      :delete {:summary (sd/sum_adm "Delete full_text.")
               :coercion reitit.coercion.spec/coercion
               :parameters {:path {:media_resource_id uuid?}}
               :middleware [wrap-authorize-admin!
                            (wrap-find-full_text :media_resource_id true)]
               :responses {200 {:description "Returns the deleted full_text."
                                :body ::response-schema-def}
                           406 {:description "Deletion failed."
                                :schema string?
                                :examples {"application/json" {:message "Could not delete full_text."}}}}
               :handler handle_delete-full_texts}}]]])

; TODO full_texts: test wrap auth for collection
(def collection-routes
  [["/collection/:collection_id/full_text"
    {:openapi {:tags ["collection/full_text"]}}
    ["/"
     {:get {:summary (sd/sum_usr_pub "Get full_text.")
            :handler handle_get-full_text
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:collection_id s/Str}}
            :responses {200 {:description "Returns the full_text."
                             :body s/Any}}
            :middleware [jqh/ring-wrap-add-media-resource
                         jqh/ring-wrap-authorization-edit-metadata
                         (wrap-find-full_text :collection_id true)]}

      :post {:summary (sd/sum_usr "Create full_text for collection")
             :swagger {:consumes "application/json"
                       :produces "application/json"}
             :handler handle_create-full_texts
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:collection_id s/Str}
                          :body {:text s/Str}}
             :responses {200 {:description "Returns the created full_text."
                              :body s/Any}
                         406 {:description "Creation failed."
                              :schema s/Str
                              :examples {"application/json" {:message "Could not create full_text."}}}}
             :middleware [jqh/ring-wrap-add-media-resource
                          jqh/ring-wrap-authorization-edit-metadata]}

      :put {:summary (sd/sum_usr "Update full_text for collection.")
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:collection_id s/Str}
                         :body {:text s/Str}}
            :middleware [jqh/ring-wrap-add-media-resource
                         jqh/ring-wrap-authorization-edit-metadata
                         (wrap-find-full_text :collection_id true)]
            :responses {200 {:description "Returns the updated full_text."
                             :body s/Any}
                        406 {:description "Update failed."
                             :schema s/Str
                             :examples {"application/json" {:message "Could not update full_text."}}}}
            :handler handle_update-full_texts}

      :delete {:summary (sd/sum_usr "Delete full_text.")
               :coercion reitit.coercion.schema/coercion
               :parameters {:path {:collection_id s/Str}}
               :middleware [jqh/ring-wrap-add-media-resource
                            jqh/ring-wrap-authorization-edit-metadata
                            (wrap-find-full_text :collection_id true)]
               :responses {200 {:description "Returns the deleted full_text."
                                :body s/Any}
                           406 {:description "Deletion failed."
                                :schema s/Str
                                :examples {"application/json" {:message "Could not delete full_text."}}}}
               :handler handle_delete-full_texts}}]]])

; TODO full_texts: test wrap auth for media entry
(def entry-routes
  [["/media-entry/:media_entry_id/full_text"
    {:openapi {:tags ["media-entry/full_text"]}}
    ["/"
     {:get {:summary (sd/sum_usr_pub "Get full_text.")
            :handler handle_get-full_text
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Str}}
            :responses {200 {:description "Returns the full_text."
                             :body s/Any}}
            :middleware [jqh/ring-wrap-add-media-resource
                         jqh/ring-wrap-authorization-view
                         (wrap-find-full_text :media_entry_id true)]}

      :post {:summary (sd/sum_usr "Create full_text for collection")
             :swagger {:consumes "application/json"
                       :produces "application/json"}
             :handler handle_create-full_texts
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:media_entry_id s/Str}
                          :body {:text s/Str}}
             :responses {200 {:description "Returns the created full_text."
                              :body s/Any}
                         406 {:description "Creation failed."
                              :schema s/Str
                              :examples {"application/json" {:message "Could not create full_text."}}}}
             :middleware [jqh/ring-wrap-add-media-resource
                          jqh/ring-wrap-authorization-edit-metadata]}

      :put {:summary (sd/sum_usr "Update full_text for collection.")
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Str}
                         :body {:text s/Str}}
            :middleware [jqh/ring-wrap-add-media-resource
                         jqh/ring-wrap-authorization-edit-metadata
                         (wrap-find-full_text :media_entry_id true)]
            :responses {200 {:description "Returns the updated full_text."
                             :body s/Any}
                        406 {:description "Update failed."
                             :schema s/Str
                             :examples {"application/json" {:message "Could not update full_text."}}}}
            :handler handle_update-full_texts}

      :delete {:summary (sd/sum_usr "Delete full_text.")
               :coercion reitit.coercion.schema/coercion
               :parameters {:path {:media_entry_id s/Str}}
               :middleware [jqh/ring-wrap-add-media-resource
                            jqh/ring-wrap-authorization-edit-metadata
                            (wrap-find-full_text :media_entry_id true)]
               :responses {200 {:description "Returns the deleted full_text."
                                :body s/Any}
                           406 {:description "Deletion failed."
                                :schema s/Str
                                :examples {"application/json" {:message "Could not delete full_text."}}}}
               :handler handle_delete-full_texts}}]]])
