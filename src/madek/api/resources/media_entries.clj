(ns madek.api.resources.media-entries
  (:require [clj-uuid]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as sa]
            [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]
            [madek.api.authorization :as authorization]
            [madek.api.constants :refer [FILE_STORAGE_DIR]]
            [madek.api.resources.media-entries.index :refer [get-index
                                                             get-index_related_data]]
            [madek.api.resources.media-entries.media-entry :refer [get-media-entry]]
            [madek.api.resources.shared.core :as sd]
            [madek.api.resources.shared.db_helper :as dbh]
            [madek.api.resources.shared.json_query_param_helper :as jqh]
            [madek.api.utils.auth :refer [wrap-authorize-admin!]]
            [madek.api.utils.coercion.spec-alpha-definition :as sp]
            [madek.api.utils.coercion.spec-alpha-definition-map :as sp-map]
            [madek.api.utils.coercion.spec-alpha-definition-nil :as sp-nil]
            [madek.api.utils.helper :refer [convert-map-if-exist to-uuid]]
            [next.jdbc :as jdbc]
            [reitit.coercion.schema]
            [reitit.coercion.spec :as spec]
            [reitit.ring.middleware.multipart :as multipart]
            [schema.core :as s]
            [spec-tools.core :as st]
            [taoensso.timbre :refer [info]]))

(defn handle_query_media_entry [req]
  (get-index req))

(defn handle_query_media_entry-related-data [req]
  (get-index_related_data req))

(defn handle_get-media-entry [req]
  (let [query-params (-> req :parameters :query)
        qreq (assoc-in req [:query-params] query-params)]
    (get-media-entry qreq)))

; TODO try catch
(defn handle_delete_media_entry [req]
  (let [eid (-> req :parameters :path :media_entry_id)
        mr (-> req :media-resource)
        tx (:tx req)
        sql-query-entries (-> (sql/update :media_entries)
                              (sql/set {:deleted_at (java.util.Date.)})
                              (sql/where [:= :id eid])
                              sql-format)
        dresult (jdbc/execute! tx sql-query-entries)]

    (info "handle_delete_media_entry"
          "\n eid: \n" eid
          "\n dresult: \n" dresult)
    (if (= 1 (::jdbc/update-count (first dresult)))
      (sd/response_ok {:deleted mr})
      (sd/response_failed "Failed to delete media entry" 406))))

(defn- get-context-keys-4-context [contextId tx]
  (map :meta_key_id
       (dbh/query-eq-find-all :context_keys :context_id (to-uuid contextId) tx)))

(defn- check-has-meta-data-for-context-key [meId mkId tx]
  (let [md (dbh/query-eq-find-one :meta_data :media_entry_id (to-uuid meId) :meta_key_id mkId tx)
        hasMD (not (nil? md))
        result {(keyword mkId) hasMD}]
    result))

(defn handle_try-publish-media-entry [req]
  "Checks all Contexts in AppSettings-contexts_for_entry_validation.
   All the meta-data for the meta-keys have to be set.
   In that case, the is_publishable of the entry is set to true."
  (let [eid (-> req :parameters :path :media_entry_id)
        tx (:tx req)
        validationContexts (-> (dbh/query-find-all :app_settings :contexts_for_entry_validation tx)
                               first
                               :contexts_for_entry_validation)
        contextKeys (first (map get-context-keys-4-context validationContexts))
        hasMetaData (for [cks contextKeys]
                      (check-has-meta-data-for-context-key eid cks tx))
        tf (for [elem hasMetaData] (vals elem))
        publishable (reduce (fn [tfresult tfval] (and tfresult (first tfval))) [true] tf)]

    (info "handle_try-publish-media-entry"
          "\n eid: \n" eid
          "\n validationContexts: \n" validationContexts
          "\n contextKeys: \n" contextKeys
          "\n hasMetaData: \n" hasMetaData
          "\n tf: \n" tf
          "\n publishable: \n" publishable)
    (if (true? publishable)
      (let [data {:is_published true}
            eid (to-uuid eid)
            sql-query (-> (sql/update :media_entries)
                          (sql/set data)
                          (sql/where [:= :id eid])
                          sql-format)
            dresult (jdbc/execute-one! tx sql-query)]

        (info "handle_try-publish-media-entry"
              "\n published: entry_id: \n" eid
              "\n dresult: \n" dresult)

        (if (= 1 (::jdbc/update-count dresult))
          (sd/response_ok (dbh/query-eq-find-one :media_entries :id eid tx))
          (sd/response_failed "Could not update publish on media_entry." 406)))

      (sd/response_failed
       {:is_publishable publishable
        :media_entry_id eid
        :has_meta_data hasMetaData}
       406))))

(defn handle_update-media-entry [req]
  (let [data (-> req :parameters :body)
        tx (:tx req)
        eid (-> req :parameters :path :media_entry_id)
        eid (to-uuid eid)
        sql-query (-> (sql/update :media_entries)
                      (sql/set (convert-map-if-exist data))
                      (sql/where [:= :id eid])
                      (sql/returning :*)
                      sql-format)
        dresult (jdbc/execute-one! tx sql-query)]
    (if dresult
      (sd/response_ok dresult)
      (sd/response_failed "Could not update publish on media_entry." 406))))

(def Madek-Constants-Default-Mime-Type "application/octet-stream")

(defn extract-extension [filename]
  (let [match (re-find #"\.[A-Za-z0-9]+$" filename)]
    match))

(defn new_media_file_attributes
  [file user-id mime]
  {:uploader_id user-id
   ;:content_type Madek-Constants-Default-Mime-Type
   :content_type mime
   :filename (:filename file)
   :extension (extract-extension (:filename file))
   :size (:size file)
   :guid (clj-uuid/v4)
   :access_hash (clj-uuid/v4)})

(def MC-FILE_STORAGE_DIR "tmp/originals")

(defn original-store-location [mf]
  (let [guid (:guid mf)
        loc (apply str FILE_STORAGE_DIR "/" (first guid) "/" guid)]
    ;(info "\nstore-location\n" "\nmf\n" mf "\nguid\n" guid "\nloc\n" loc)
    loc))

(defn handle_uploaded_file_resp_ok
  "Handles the uploaded file and sends an ok response."
  [file media-file media-entry collection-id tx]
  (let [temp-file (-> file :tempfile)
        temp-path (.getPath temp-file)
        store-location (original-store-location media-file)]
    ; copy file
    (io/copy (io/file temp-path) (io/file store-location))
    (sd/response_ok {:media_entry (assoc media-entry :media_file media-file)})))

; We dont do add meta-data or collection.
; This is done via front-end.
;(me_add-default-license new-mer)
;(me_exract-and-store-metadata new-mer)
;(me_add-to-collection new-mer (or col_id_param (-> workflow :master_collection :id)))
;(if-let [collection (dbh/query-eq-find-one "collections" "id" collection-id)]
;  (if-let [add-col-res (collection-media-entry-arcs/create-col-me-arc collection-id (:id media-entry) {} tx)]
;    (info "handle_uploaded_file_resp_ok: added to collection: " collection-id "\nresult\n" add-col-res)
;    (error "Failed: handle_uploaded_file_resp_ok: add to collection: " collection-id))
;    (sd/response_ok {:media_entry (assoc media-entry :media_file media-file :collection_id collection-id)})
;    (sd/response_ok {:media_entry (assoc media-entry :media_file media-file)}))

;))

(defn create-media_entry
  "Only for testing. Does not trigger media convert. So previews are missing."
  [file auth-entity mime collection-id tx]
  (let [user-id (:id auth-entity)
        new-me {:responsible_user_id (str user-id)
                :creator_id (str user-id)
                :is_published false}]
    ; handle workflow authorize

    (let [new-me {:responsible_user_id (str user-id)
                  :creator_id (str user-id)
                  :is_published false}
          sql-query (-> (sql/insert-into :media_entries)
                        (sql/values [(convert-map-if-exist new-me)])
                        sql-format)
          new-mer (jdbc/execute! tx sql-query)]
      (if new-mer
        (let [me-id (:id new-mer)
              mf (new_media_file_attributes file user-id mime)
              new-mf (assoc mf :media_entry_id me-id)
              sql-query (-> (sql/insert-into :media_files)
                            (sql/values [(convert-map-if-exist new-mf)])
                            sql-format)
              new-mfr (jdbc/execute-one! tx sql-query)]

          (info "\ncreate-me: " "\ncreated media-entry: " new-mer "\nnew media-file: " new-mf)

          (if new-mfr
            (handle_uploaded_file_resp_ok file new-mfr new-mer collection-id tx)
            (sd/response_failed "Could not create media-file" 406)))
        (sd/response_failed "Could not create media-entry" 406)))))

; this is only for dev
; no collection add
; no meta data / entry clone
; no workflows
; no preview generation
; no file media conversion
; use madek web-app to upload files and create entries.
(defn handle_create-media-entry [req]
  (let [copy-md-id (-> req :parameters :query :copy_me_id)
        collection-id (-> req :parameters :query :collection_id)
        tx (:tx req)
        file (-> req :parameters :multipart :file)
        file-content-type (-> file :content-type)
        temppath (.getPath (:tempfile file))
        auth (-> req :authenticated-entity)]

    (info "handle_create-media-entry"
          "\nauth\n" (:id auth)
          "\ncopy_md\n" copy-md-id
          "\ncollection-id\n" collection-id
          "\nfile\n" file
          "\n content: " file-content-type
          "\ntemppath\n" temppath)

    (let [;mime (or file-content-type (mime-type-of temppath) )
          mime file-content-type]

      (info "handle_create-media-entry" "\nmime-type\n" mime)
      (if (nil? auth)
        (sd/response_failed "Not authenticated" 406)
        (create-media_entry file auth mime collection-id tx)))))

(def ISO8601TimestampWithoutMS
  (s/constrained
   s/Str
   #(re-matches #"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z" %)
   "ISO 8601 timestamp without milliseconds"))

(sa/def ::media-entries-def
  (sa/keys :opt-un
           [::sp/collection_id ::sp/order ::sp/filter_by
            ::sp/me_get_metadata_and_previews ::sp/me_get_full_size
            ::sp/me_edit_metadata ::sp/me_edit_permissions
            ::sp/public_get_metadata_and_previews ::sp/public_get_full_size
            ::sp/full_data
            ::sp/page ::sp/size]))

(sa/def ::media-entries-adm-def
  (sa/keys :opt-un
           [::sp/collection_id ::sp/order ::sp/filter_by
            ::sp/me_get_metadata_and_previews ::sp/me_get_full_size
            ::sp/me_edit_metadata ::sp/me_edit_permissions
            ::sp/public_get_metadata_and_previews ::sp/public_get_full_size
            ::sp/full_data
            ::sp/page ::sp/size
            ::sp-map/filter_softdelete]))

(sa/def ::media-entry-def
  (sa/keys :req-un [::sp/id]
           :opt-un
           [::sp/creator_id ::sp/responsible_user_id ::sp/get_full_size ::sp/get_metadata_and_previews
            ::sp/is_published ::sp/created_at ::sp/updated_at ::sp/edit_session_updated_at ::sp/meta_data_updated_at
            ::sp-nil/responsible_delegation_id
            ::sp/page ::sp/size]))

(def schema_export_media_entry
  {:id s/Uuid
   (s/optional-key :creator_id) s/Uuid
   (s/optional-key :responsible_user_id) s/Uuid
   (s/optional-key :get_full_size) s/Bool
   (s/optional-key :get_metadata_and_previews) s/Bool
   (s/optional-key :is_published) s/Bool

   (s/optional-key :created_at) s/Any
   (s/optional-key :updated_at) s/Any

   (s/optional-key :edit_session_updated_at) s/Any
   (s/optional-key :meta_data_updated_at) s/Any

   (s/optional-key :responsible_delegation_id) (s/maybe s/Uuid)})

(def schema_export_adm_media_entry
  (merge schema_export_media_entry
         {:deleted_at (s/maybe ISO8601TimestampWithoutMS)}))

(sa/def ::media-entry-response-def
  (sa/keys :req-un [::sp/media_entries ::sp/meta_data ::sp/media_files ::sp/previews]
           :opt-un [::sp/col_arcs ::sp/col_meta_data]))

;; -----------------------

(sa/def :me1/media_entry
  (st/spec {:spec (sa/keys :req-un [::sp/id]
                           :opt-un [::sp/responsible_user_id
                                    ::sp/get_full_size
                                    ::sp/creator_id
                                    ::sp/updated_at
                                    ::sp/edit_session_updated_at
                                    ::sp/is_published
                                    ::sp/get_metadata_and_previews
                                    ::sp/meta_data_updated_at
                                    ::sp/created_at])
            :description "Represents a media entry with optional metadata"}))

;; FIXME: can be null
(sa/def :me1/meta_data (st/spec {:spec any?}))

;; FIXME: can be null
(sa/def :me1/media_files (st/spec {:spec any?}))

;; FIXME: can be null
(sa/def :me1/previews (st/spec {:spec any?}))

(sa/def :me1/media_entries
  (st/spec {:spec (sa/coll-of :me1/media_entry)
            :description "An array of media entries"}))

(sa/def ::media-entry-response2-def
  (st/spec {:spec (sa/keys :req-un [:me1/media_entries :me1/meta_data :me1/media_files :me1/previews])}))

;; -----------------------

(sa/def ::media-entries-resp-def
  (sa/keys :opt-un
           [::sp/responsible_user_id ::sp/get_full_size ::sp/creator_id ::sp/updated_at
            ::sp/edit_session_updated_at ::sp/is_published ::sp/get_metadata_and_previews ::sp/meta_data_updated_at
            ::sp/created_at]
           :req-un [::sp/id]))

(sa/def :media-entry-list/media_entries
  (st/spec
   {:spec (sa/coll-of ::media-entries-resp-def)
    :description "A list of media-entries"}))

(sa/def ::media-entries-body-resp-def
  (sa/keys :req-un [:media-entry-list/media_entries]))

(def schema_publish_failed
  {:message {:is_publishable s/Bool
             :media_entry_id s/Uuid
             :has_meta_data [{s/Any s/Bool}]}})

(def schema_media_entry
  {:id s/Uuid
   :creator_at s/Any
   :creator_id s/Uuid
   :responsible_user_id s/Uuid
   :is_published s/Bool
   :updated_at s/Any
   :edit_session_updated_at s/Any
   :meta_data_updated_at s/Any})

(def schema_media_entry_deleted
  {:id s/Uuid
   :deleted_at s/Any
   :created_at s/Any
   :creator_id s/Uuid
   :responsible_user_id s/Uuid
   :responsible_delegation_id (s/maybe s/Uuid)
   :is_published s/Bool
   :updated_at s/Any
   :get_full_size s/Bool
   :type s/Any
   :table-name s/Str
   :get_metadata_and_previews s/Bool
   :edit_session_updated_at s/Any
   :meta_data_updated_at s/Any})

(def ring-routes
  ["/"
   {:openapi {:tags ["api/media-entries"]}}
   ["media-entries"
    {:get
     {:summary (sd/?sum_pub? "Query media-entries.")
      :handler handle_query_media_entry
      :middleware [jqh/ring-wrap-parse-json-query-parameters]
      :coercion spec/coercion
      :parameters {:query ::media-entries-def}
      :responses {200 {:description "Returns the media-entries."
                       :body ::media-entries-body-resp-def}
                  422 {:description "Unprocessable Entity."
                       :body any?}}}}]
   ["media-entries-related-data"
    {:get
     {:summary (sd/?sum_usr? "Query media-entries with all related data.")
      :handler handle_query_media_entry-related-data
      :middleware [jqh/ring-wrap-parse-json-query-parameters]
      :coercion spec/coercion
      :parameters {:query ::media-entries-def}
      :responses {200 {:description "Returns the media-entries with all related data."
                       :body ::media-entry-response2-def}}}}]])

(def ring-admin-routes
  ["/"
   {:openapi {:tags ["admin/media-entries"]}}

   ["media-entries"
    {:get
     {:summary "Query media-entries."
      :handler handle_query_media_entry
      :middleware [wrap-authorize-admin!
                   jqh/ring-wrap-parse-json-query-parameters]
      :coercion spec/coercion
      :parameters {:query ::media-entries-adm-def}
      :responses {200 {:description "Returns the media-entries."
                       :body ::media-entries-body-resp-def}
                  422 {:description "Unprocessable Entity."
                       :body any?}}}}]

   ["media-entries/:media_entry_id"
    {:put {:summary "Try publish media-entry for id / HERE!!!!"
           :handler handle_update-media-entry
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :middleware [wrap-authorize-admin!]
           :accept "application/json"
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Uuid}
                        :body {:deleted_at (s/maybe ISO8601TimestampWithoutMS)}}
           :responses {200 {:description "Returns the updated media-entry."
                            :body schema_export_adm_media_entry}
                       406 {:description "Not Acceptable."
                            :body schema_publish_failed}}}}]])

(sa/def ::copy_me_id string?)
(sa/def ::collection_id string?)
(def media-entry-routes
  ["/media-entry"
   {:openapi {:tags ["api/media-entry"]}}
   ["/"
    {:post {:summary (sd/sum_todo "Create media-entry. Only for testing. Use webapp until media-encoder is ready")
            :handler handle_create-media-entry
            :swagger {:consumes "multipart/form-data"
                      :produces "application/json"}
            :content-type "application/json"
            :accept "multipart/form-data"
            :parameters {:query (sa/keys :opt-un [::copy_me_id ::collection_id])
                         :multipart {:file multipart/temp-file-part}}
            :middleware [authorization/wrap-authorized-user]
            :coercion spec/coercion
            ;; TODO: missing swagger-file-upload-button, coercion
            :responses {200 {:description "Returns the created media-entry."
                             :body any?}
                        406 {:description "Could not create media-entry."
                             :body any?}}}}]

   ["/:media_entry_id"
    {:get {:summary (sd/?token? "Get media-entry for id.")
           :handler handle_get-media-entry
           :swagger {:produces "application/json"}
           :content-type "application/json"

           :middleware [jqh/ring-wrap-add-media-resource
                        jqh/ring-wrap-authorization-view]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Uuid}}
           :responses {200 {:description "Returns the media-entry."
                            :body schema_media_entry}
                       404 {:description "Not found."
                            :body s/Any}}}

     ; TODO Frage: wer kann einen Eintrag löschen
     :delete {:summary "Delete media-entry for id."
              :handler handle_delete_media_entry
              :swagger {:produces "application/json"}
              :content-type "application/json"

              :middleware [jqh/ring-wrap-add-media-resource
                           jqh/ring-wrap-authorization-edit-permissions]
              :coercion reitit.coercion.schema/coercion
              :responses {200 {:description "Returns the deleted media-entry."
                               :body {:deleted schema_media_entry_deleted}}
                          406 {:description "Not Acceptable."
                               :body s/Any}}
              :parameters {:path {:media_entry_id s/Uuid}}}}]

   ["/:media_entry_id/publish"
    {:put {:summary "Try publish media-entry for id."
           :handler handle_try-publish-media-entry
           :swagger {:produces "application/json"}
           :content-type "application/json"

           :middleware [jqh/ring-wrap-add-media-resource
                        jqh/ring-wrap-authorization-edit-metadata]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Uuid}}
           :responses {200 {:description "Returns the updated media-entry."
                            :body schema_export_media_entry}
                       406 {:description "Not Acceptable."
                            :body schema_publish_failed}}}}]])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
