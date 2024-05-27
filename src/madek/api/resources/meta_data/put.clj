(ns madek.api.resources.meta_data.put
  (:require [cheshire.core]
            [cheshire.core :as cheshire]
            [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]
            [logbug.catcher :as catcher]
            [madek.api.resources.meta_data.common :refer :all]
            [madek.api.resources.shared.core :as sd]
            [madek.api.resources.shared.json_query_param_helper :as jqh]
            [madek.api.utils.helper :refer [convert-map-if-exist to-uuid]]
            [next.jdbc :as jdbc]
            [reitit.coercion.schema]
            [reitit.coercion.spec]
            [schema.core :as s]
            [taoensso.timbre :refer [info]]))

(defn- sql-cls-upd-meta-data-typed-id [stmt mr mk-id md-type]
  (let [column (col-key-for-mr-type mr)
        md-sql (-> stmt
                   (sql/where [:and
                               [:= :meta_key_id mk-id]
                               [:= :type md-type]
                               [:= column (to-uuid (-> mr :id) column)]]))]
    md-sql))

(defn- handle_update-meta-data-text-base
  [req md-type upd-data]
  (try
    (catcher/with-logging {}
      (let [mr (-> req :media-resource)
            meta-key-id (-> req :parameters :path :meta_key_id)
            sql-query (-> (sql/update :meta_data)
                          (sql/set (convert-map-if-exist upd-data))
                          (sql-cls-upd-meta-data-typed-id mr meta-key-id md-type)
                          sql-format)
            tx (:tx req)
            upd-result (jdbc/execute-one! tx sql-query)
            result-data (db-get-meta-data mr meta-key-id md-type tx)]

        (sd/logwrite req (str "handle_update-meta-data-text-base:"
                              " mr-id: " (:id mr)
                              " mr-type: " (:type mr)
                              " md-type: " md-type
                              " meta-key-id: " meta-key-id
                              " upd-result: " upd-result))

        (if (= 1 (::jdbc/update-count upd-result))
          (sd/response_ok result-data)
          (sd/response_failed {:message "Failed to update meta data text base"} 406))))
    (catch Exception ex (sd/response_exception ex))))

(defn handle_update-meta-data-text
  [req]
  (let [text-data (-> req :parameters :body :string)
        upd-data {:string text-data}
        md-type "MetaDatum::Text"]

    (info "handle_update-meta-data-text" "\nupd-data\n" upd-data)
    (handle_update-meta-data-text-base req md-type upd-data)))

(defn handle_update-meta-data-text-date
  [req]
  (let [text-data (-> req :parameters :body :string)
        upd-data {:string text-data}
        ; TODO multi line ? or other params
        md-type "MetaDatum::TextDate"]
    (info "handle_update-meta-data-text-date" "\nupd-data\n" upd-data)
    (handle_update-meta-data-text-base req md-type upd-data)))

(defn handle_update-meta-data-json
  [req]
  (let [text-data (-> req :parameters :body :json)
        json-parsed (cheshire/parse-string text-data)
        ;upd-data {:json json-parsed}
        upd-data {:json (with-meta json-parsed {:pgtype "jsonb"})}
        md-type "MetaDatum::JSON"]
    (info "handle_update-meta-data-json"
          "\nupd-data\n" upd-data)
    (handle_update-meta-data-text-base req md-type upd-data)))

(def media_entry.meta_key_id.json {:summary "Update meta-data json for media-entry"
                                   :handler handle_update-meta-data-json
                                   :middleware [jqh/ring-wrap-add-media-resource
                                                jqh/ring-wrap-authorization-edit-metadata]
                                   :coercion reitit.coercion.schema/coercion
                                   :parameters {:path {:media_entry_id s/Uuid
                                                       :meta_key_id s/Str}
                                                :body {:json s/Any}}
                                   :responses {200 {:body s/Any}}})

(def meta_key_id.text-date {:summary "Update meta-data text-date for media-entry"
                            :handler handle_update-meta-data-text-date
                            :middleware [jqh/ring-wrap-add-media-resource
                                         jqh/ring-wrap-authorization-edit-metadata]
                            :coercion reitit.coercion.schema/coercion
                            :parameters {:path {:media_entry_id s/Uuid
                                                :meta_key_id s/Str}
                                         :body {:string s/Str}}
                            :responses {200 {:body s/Any}}})

(def media_entry.meta_key_id.text {:summary "Update meta-data text for media-entry"
                                   :handler handle_update-meta-data-text
                                   :middleware [jqh/ring-wrap-add-media-resource
                                                jqh/ring-wrap-authorization-edit-metadata]
                                   :coercion reitit.coercion.schema/coercion
                                   :parameters {:path {:media_entry_id s/Uuid
                                                       :meta_key_id s/Str}
                                                :body {:string s/Str}}
                                   :responses {200 {:body s/Any}}})

(def collection.meta_key_id.json {:summary "Update meta-data json for collection."
                                  :handler handle_update-meta-data-json
                                  :middleware [jqh/ring-wrap-add-media-resource
                                               jqh/ring-wrap-authorization-edit-metadata]
                                  :coercion reitit.coercion.schema/coercion
                                  :parameters {:path {:collection_id s/Uuid
                                                      :meta_key_id s/Str}
                                               :body {:json s/Any}}
                                  :responses {200 {:body s/Any}}})

(def text.meta_key_id.text-date {:summary "Update meta-data text-date for collection."
                                 :handler handle_update-meta-data-text-date
                                 :middleware [jqh/ring-wrap-add-media-resource
                                              jqh/ring-wrap-authorization-edit-metadata]
                                 :coercion reitit.coercion.schema/coercion
                                 :parameters {:path {:collection_id s/Uuid
                                                     :meta_key_id s/Str}
                                              :body {:string s/Str}}
                                 :responses {200 {:body s/Any}}})

(def meta_key_id.text {:summary "Update meta-data text for collection."
                       :handler handle_update-meta-data-text
                       :middleware [jqh/ring-wrap-add-media-resource
                                    jqh/ring-wrap-authorization-edit-metadata]
                       :accept "application/json"
                       :content-type "application/json"
                       :swagger {:produces "application/json" :consumes "application/json"}
                       :coercion reitit.coercion.schema/coercion
                       :parameters {:path {:collection_id s/Uuid
                                           :meta_key_id s/Str}
                                    :body {:string s/Str}}
                       :responses {200 {:body s/Any}}})