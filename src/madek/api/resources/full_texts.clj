(ns madek.api.resources.full-texts
   (:require [clojure.java.jdbc :as jdbc]
             [clojure.tools.logging :as logging]
             [logbug.catcher :as catcher]
             [madek.api.pagination :as pagination]
             [madek.api.resources.shared :as sd]
             [madek.api.utils.auth :refer [wrap-authorize-admin!]]
             [madek.api.utils.rdbms :as rdbms]
             [madek.api.utils.sql :as sql]
             [reitit.coercion.schema]
             [schema.core :as s]))

(defn handle_list-full_texts
  [req]
  (let [query-params (-> req :parameters :query)
        full_data (:full_data query-params)
        base-query (if (true? full_data)
                     (sql/select :*)
                     (sql/select :media_resource_id))
        db-query (-> base-query
                     (sql/from :full_texts)
                     (sd/build-query-param query-params :media_resource_id)
                     (sd/build-query-param-like query-params :text)
                     (pagination/add-offset-for-honeysql query-params)
                     sql/format)

        db-result (jdbc/query (rdbms/get-ds) db-query)]
    
    (logging/info "handle_list-full_texts:" "\nquery:\n" db-query)
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
            ins-res (jdbc/insert! (rdbms/get-ds) :full_texts ins-data)]

        (logging/info "handle_create-full_texts: " "\nnew-data:\n" ins-data "\nresult:\n" ins-res)

        (if-let [result (first ins-res)]
          (sd/response_ok result)
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
        ;old-data (-> req :full_text)
            upd-query (sd/sql-update-clause "media_resource_id" (str mr-id))
            upd-result (jdbc/update! (rdbms/get-ds) :full_texts dwid upd-query)]

        (logging/info "handle_update-full_texts: " mr-id "\new-data:\n" dwid "\nresult:\n" upd-result)

        (if (= 1 (first upd-result))
          (sd/response_ok (sd/query-eq-find-one :full_texts :media_resource_id mr-id))
          (sd/response_failed "Could not update full_text." 406))))
    (catch Exception e (sd/response_exception e)))
  )

(defn handle_delete-full_texts
  [req]
  (try
    (catcher/with-logging {}
      (let [full-text (-> req :full_text)
            mr-id (:media_resource_id full-text)
            del-result (jdbc/delete! (rdbms/get-ds)
                                     :full_texts
                                     ["media_resource_id = ?" mr-id])]

        (logging/info "handle_delete-full_texts: " mr-id " result: " del-result)

        (if (= 1 (first del-result))
          (sd/response_ok full-text)
          (logging/error "Could not delete full_text " mr-id))))
    (catch Exception e (sd/response_exception e))))


(defn wrap-find-full_text [param send404]
  (fn [handler]
    (fn [request] (sd/req-find-data request handler param
                                    :full_texts :media_resource_id
                                    :full_text send404))))


; TODO tests
; TODO howto access control or full_texts is public meta data
(def query-routes
  [
  ["/full_texts"
    {:get {:summary (sd/sum_usr "Query or list full_texts.")
           :handler handle_list-full_texts
           :coercion reitit.coercion.schema/coercion
           :parameters {:query {(s/optional-key :full_data) s/Bool
                                (s/optional-key :media_resource_id) s/Uuid
                                (s/optional-key :text) s/Str
                                (s/optional-key :page) s/Str
                                (s/optional-key :count) s/Str
                                }}}}]
   ["/full_texts/:media_resource_id"
    {:get {:summary (sd/sum_usr "Get full_text.")
           :handler handle_get-full_text
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_resource_id s/Str}}
           :middleware [(wrap-find-full_text :media_resource_id true)]
           }}]
   ])
     
; TODO tests
; TODO Frage: siehe web-app: ??
; wer darf full-texts anlegen?
; nur der admin?


(def edit-routes
  [
   ["/full_text"
    {:post {:summary (sd/sum_adm "Create full_texts entry")
            :swagger {:consumes "application/json" :produces "application/json"}
            :handler handle_create-full_texts
            :coercion reitit.coercion.schema/coercion
            :parameters {:body {:text s/Str
                                :media_resource_id s/Str}}
           :middleware [wrap-authorize-admin!]
            }}]
   
   ["/full_text/:media_resource_id"
    {:post {:summary (sd/sum_adm "Create full_texts entry")
            :swagger {:consumes "application/json" :produces "application/json"}
            :handler handle_create-full_texts
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_resource_id s/Str}
                         :body {:text s/Str}}
           :middleware [wrap-authorize-admin!]
            }
     :put {:summary (sd/sum_adm "Update full_text.")
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_resource_id s/Str}
                        :body {:text s/Str}}
           :middleware [wrap-authorize-admin!
                        (wrap-find-full_text :media_resource_id true)]
           :handler handle_update-full_texts}

    :delete {:summary (sd/sum_adm "Delete full_text.")
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:media_resource_id s/Str}}
             :middleware [wrap-authorize-admin!
                          (wrap-find-full_text :media_resource_id true)]
             :handler handle_delete-full_texts}}
   ]
  ]
  )

; TODO full_texts: test wrap auth for collection
(def collection-routes
  [["/collection/:collection_id/full_text"
    {:get {:summary (sd/sum_usr "Get full_text.")
           :handler handle_get-full_text
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:collection_id s/Str}}
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-metadata
                        (wrap-find-full_text :collection_id true)]}
     
     :post {:summary (sd/sum_adm "Create full_text for collection")
            :swagger {:consumes "application/json"
                      :produces "application/json"}
            :handler handle_create-full_texts
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:collection_id s/Str}
                         :body {:text s/Str}}
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-metadata]}
     
     :put {:summary (sd/sum_adm "Update full_text for collection.")
             :coercion reitit.coercion.schema/coercion
             :parameters {:path {:collection_id s/Str}
                          :body {:text s/Str}}
             :middleware [sd/ring-wrap-add-media-resource
                          sd/ring-wrap-authorization-edit-metadata
                          (wrap-find-full_text :collection_id true)]
             :handler handle_update-full_texts}

     :delete {:summary (sd/sum_adm "Delete full_text.")
              :coercion reitit.coercion.schema/coercion
              :parameters {:path {:collection_id s/Str}}
              :middleware [sd/ring-wrap-add-media-resource
                           sd/ring-wrap-authorization-edit-metadata
                           (wrap-find-full_text :collection_id true)]
              :handler handle_delete-full_texts}}]])


; TODO full_texts: test wrap auth for media entry
(def entry-routes
  [["/media-entry/:media_entry_id/full_text"
    {:get {:summary (sd/sum_usr "Get full_text.")
           :handler handle_get-full_text
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Str}}
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-view
                        (wrap-find-full_text :media_entry_id true)]}

     :post {:summary (sd/sum_adm "Create full_text for collection")
            :swagger {:consumes "application/json"
                      :produces "application/json"}
            :handler handle_create-full_texts
            :coercion reitit.coercion.schema/coercion
            :parameters {:path {:media_entry_id s/Str}
                         :body {:text s/Str}}
            :middleware [sd/ring-wrap-add-media-resource
                         sd/ring-wrap-authorization-edit-metadata]}

     :put {:summary (sd/sum_adm "Update full_text for collection.")
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Str}
                        :body {:text s/Str}}
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-metadata
                        (wrap-find-full_text :media_entry_id true)]
           :handler handle_update-full_texts}

     :delete {:summary (sd/sum_adm "Delete full_text.")
              :coercion reitit.coercion.schema/coercion
              :parameters {:path {:media_entry_id s/Str}}
              :middleware [sd/ring-wrap-add-media-resource
                           sd/ring-wrap-authorization-edit-metadata
                           (wrap-find-full_text :media_entry_id true)]
              :handler handle_delete-full_texts}}]])
