(ns madek.api.resources.media-entries
  (:require [clj-uuid]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as sa]
            [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]
            [madek.api.authorization :as authorization]
            [madek.api.constants :refer [FILE_STORAGE_DIR]]
            [madek.api.db.dynamic_schema.common :refer [get-schema]]
            [madek.api.resources.media-entries.index :refer [get-index
                                                             get-index_related_data]]
            [madek.api.resources.media-entries.media-entry :refer [get-media-entry]]
            [madek.api.resources.shared :as sd]
            [madek.api.utils.helper :refer [convert-map-if-exist to-uuid]]
            [next.jdbc :as jdbc]
            [reitit.coercion.schema]
            [reitit.coercion.spec]
            [reitit.ring.middleware.multipart :as multipart]
            [schema.core :as s]
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
        sql-query-files (-> (sql/delete :media_files)
                            (sql/where [:= :media_entry_id eid])
                            sql-format)
        fresult (jdbc/execute! tx sql-query-files)
        sql-query-entries (-> (sql/delete :media_entries)
                              (sql/where [:= :id eid])
                              sql-format)
        dresult (jdbc/execute! tx sql-query-entries)]

    (info "handle_delete_media_entry"
          "\n eid: \n" eid
          "\n fresult: \n" fresult
          "\n dresult: \n" dresult)
    (if (= 1 (first dresult))
      (sd/response_ok {:deleted mr})
      (sd/response_failed {:message "Failed to delete media entry"} 406))))

(defn- get-context-keys-4-context [contextId tx]
  (map :meta_key_id
       (sd/query-eq-find-all :context_keys :context_id (to-uuid contextId) tx)))

(defn- check-has-meta-data-for-context-key [meId mkId tx]
  (let [md (sd/query-eq-find-one :meta_data :media_entry_id (to-uuid meId) :meta_key_id mkId tx)
        hasMD (not (nil? md))
        result {(keyword mkId) hasMD}]
    result))

(defn handle_try-publish-media-entry [req]
  "Checks all Contexts in AppSettings-contexts_for_entry_validation.
   All the meta-data for the meta-keys have to be set.
   In that case, the is_publishable of the entry is set to true."
  (let [eid (-> req :parameters :path :media_entry_id)
        tx (:tx req)
        validationContexts (-> (sd/query-find-all :app_settings :contexts_for_entry_validation tx)
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
          (sd/response_ok (sd/query-eq-find-one :media_entries :id eid tx))
          (sd/response_failed "Could not update publish on media_entry." 406)))

      (sd/response_failed {:is_publishable publishable
                           :media_entry_id eid
                           :has_meta_data hasMetaData} 406))))

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
;(if-let [collection (sd/query-eq-find-one "collections" "id" collection-id)]
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
        (sd/response_failed "Not authed" 406)
        (create-media_entry file auth mime collection-id tx)))))

;(def schema_query_media_entries
;  {(s/optional-key :collection_id) s/Uuid
;   ; TODO order enum docu
;   ;(s/optional-key :order) (s/enum "desc" "asc" "title_asc" "title_desc" "last_change" "manual_asc" "manual_desc" "stored_in_collection")
;
;   ;; :collection_media_entry_arcs
;   (s/optional-key :order) s/Any
;   ; TODO filterby json docu
;   (s/optional-key :filter_by) s/Str
;   ;(s/optional-key :filter_by)
;   ; {
;   ;  (s/optional-key :media_entry) {:is_published s/Bool
;   ;                                 :creator_id s/Uuid
;   ;                                 :responsible_user_id s/Uuid
;   ;                                 }
;   ;  (s/optional-key :media_files) {:keys :values}
;   ;  (s/optional-key :permissions) {(s/optional-key :public) s/Bool
;   ;                                 (s/optional-key :responsible_user) s/Uuid
;   ;                                 (s/optional-key :entrusted_to_user) s/Uuid
;   ; TODO
;   ;                                 (s/optional-key :view_to_user) s/Uuid
;   ;                                 (s/optional-key :download_to_user) s/Uuid
;   ;                                 (s/optional-key :edit_md_to_user) s/Uuid
;   ;                                 (s/optional-key :edit_perms_to_user) s/Uuid
;   ;                                 (s/optional-key :entrusted_to_group) s/Uuid
;   ;                                }
;   ;  (s/optional-key :meta_data) [{(s/optional-key :type) s/Str
;   ;                               (s/optional-key :key) s/Str
;   ;                               (s/optional-key :match) s/Str
;   ;                               (s/optional-key :value) s/Str}]
;   ; TODO docu
;   ;  (s/optional-key :search) s/Str
;   ;  }
;
;   ;; media_entry_user_permission
;   (s/optional-key :me_get_metadata_and_previews) s/Bool
;   (s/optional-key :me_get_full_size) s/Bool
;
;   (s/optional-key :me_edit_metadata) s/Bool
;   (s/optional-key :me_edit_permissions) s/Bool
;
;   ;; media_entries
;   (s/optional-key :public_get_metadata_and_previews) s/Bool
;   (s/optional-key :public_get_full_size) s/Bool
;
;   (s/optional-key :page) s/Int
;   (s/optional-key :count) s/Int
;   (s/optional-key :full_data) s/Bool})

;(def schema_export_media_entry
;  {:id s/Uuid
;   (s/optional-key :creator_id) s/Uuid
;   (s/optional-key :responsible_user_id) s/Uuid
;   (s/optional-key :get_full_size) s/Bool
;   (s/optional-key :get_metadata_and_previews) s/Bool
;   (s/optional-key :is_published) s/Bool
;
;   (s/optional-key :created_at) s/Any
;   (s/optional-key :updated_at) s/Any
;
;   (s/optional-key :edit_session_updated_at) s/Any
;   (s/optional-key :meta_data_updated_at) s/Any
;
;   (s/optional-key :responsible_delegation_id) (s/maybe s/Uuid)})

;(def schema_export_col_arc
;  {:media_entry_id s/Uuid
;   :id s/Uuid
;   :order (s/maybe s/Num)
;   :position (s/maybe s/Int)
;   :created_at s/Any
;   :updated_at s/Any})

;;; TODO: not in use
;(def schema_query_media_entries_result
;  {:media_entries [(get-schema  :media-entries.schema_export_media_entry )]
;   (s/optional-key :col_arcs) [(get-schema :media-entries.schema_export_col_arc)]})

;(def schema_export_media_file
;  {:id s/Uuid
;   :media_entry_id s/Uuid
;   :conversion_profiles [s/Any]
;   :media_type (s/maybe s/Str) ; TODO enum
;   :width (s/maybe s/Int)
;   :height (s/maybe s/Int)
;   :meta_data (s/maybe s/Str)
;   :size s/Int
;   :uploader_id s/Uuid
;   :content_type s/Str
;   :access_hash s/Str
;   :extension s/Str
;   :filename s/Str
;   :guid s/Str
;   :updated_at s/Any
;   :created_at s/Any})

;(def schema_export_preview
;  {:id s/Uuid
;   :media_file_id s/Uuid
;   :media_type s/Str
;   :content_type s/Str
;   ;(s/enum "small" "small_125" "medium" "large" "x-large" "maximum")
;   :thumbnail s/Str
;   :width (s/maybe s/Int)
;   :height (s/maybe s/Int)
;   :filename s/Str
;   :conversion_profile (s/maybe s/Str)
;   :updated_at s/Any
;   :created_at s/Any})

;(def schema_export_meta_data
;  {:id s/Uuid
;   :media_entry_id (s/maybe s/Uuid)
;   :collection_id (s/maybe s/Uuid)
;
;   :type s/Str
;   :meta_key_id s/Str
;   :string (s/maybe s/Str)
;   :json (s/maybe s/Str)
;
;   :meta_data_updated_at s/Any
;   :other_media_entry_id (s/maybe s/Uuid)})

;(def schema_query_media_entries_related_result
;  {:media_entries [(get-schema  :media-entries.schema_export_media_entry )]
;   :meta_data [[(get-schema :media-entries.schema_export_meta_data)]]
;   :media_files [(s/maybe (get-schema :media-files.schema_export_media_file))]
;   :previews [[(s/maybe (get-schema :media-entries.schema_export_preview))]]
;   (s/optional-key :col_arcs) [(get-schema :media-entries.schema_export_col_arc)]
;   (s/optional-key :col_meta_data) [(get-schema :media-entries.schema_export_meta_data)]})
;
;(def schema_publish_failed
;  {:message {:is_publishable s/Bool
;             :media_entry_id s/Uuid
;             :has_meta_data [{s/Any s/Bool}]}})

(def ring-routes
  ["/"
   {:swagger {:tags ["api/media-entries"]}}
   ["media-entries"
    {:get
     {:summary "Query media-entries."
      :swagger {:produces "application/json"}
      :content-type "application/json"
      :handler handle_query_media_entry
      :middleware [sd/ring-wrap-parse-json-query-parameters]
      :coercion reitit.coercion.schema/coercion
      :parameters {:query (get-schema :media-entries.schema_query_media_entries)}
      :responses {200 {:body s/Any}
                  422 {:body s/Any}}}}]
   ["media-entries-related-data"
    {:get
     {:summary "Query media-entries with all related data."
      :swagger {:produces "application/json"}
      :content-type "application/json"
      :handler handle_query_media_entry-related-data
      :middleware [sd/ring-wrap-parse-json-query-parameters]
      :coercion reitit.coercion.schema/coercion
      :parameters {:query (get-schema :media-entries.schema_query_media_entries)}
      :responses {200 {:body (get-schema :media-entries-schema-schema_query_media_entries_related_result)}}}}]])

(sa/def ::copy_me_id string?)
(sa/def ::collection_id string?)
(def media-entry-routes
  ["/media-entry"
   {:swagger {:tags ["api/media-entry"]}}
   ["/"
    {:post {:summary (sd/sum_todo "Create media-entry. Only for testing. Use webapp until media-encoder is ready")
            :handler handle_create-media-entry
            :swagger {:consumes "multipart/form-data"
                      :produces "application/json"}
            :content-type "application/json"
            :accept "multipart/form-data"
            :middleware [authorization/wrap-authorized-user]
            ; cannot use schema, need to use spec for multiplart
            :coercion reitit.coercion.spec/coercion
            :parameters {:query (sa/keys :opt-un [::copy_me_id ::collection_id])
                         :multipart {:file multipart/temp-file-part}}}}]

   ["/:media_entry_id"
    ;{:swagger {:tags ["api/media-entry"] }}
    {:get {:summary "Get media-entry for id."
           :handler handle_get-media-entry
           :swagger {:produces "application/json"}
           :content-type "application/json"

           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-view]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Uuid}}
           :responses {200 {:body s/Any}
                       404 {:body s/Any}}}

     ; TODO Frage: wer kann einen Eintrag löschen
     :delete {:summary "Delete media-entry for id."
              :handler handle_delete_media_entry
              :swagger {:produces "application/json"}
              :content-type "application/json"

              :middleware [sd/ring-wrap-add-media-resource
                           sd/ring-wrap-authorization-edit-permissions]
              :coercion reitit.coercion.schema/coercion
              :parameters {:path {:media_entry_id s/Uuid}}}}]

   ["/:media_entry_id/publish"
    {:put {:summary "Try publish media-entry for id."
           :handler handle_try-publish-media-entry
           :swagger {:produces "application/json"}
           :content-type "application/json"

           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-metadata]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Uuid}}
           :responses {200 {:body (get-schema :media-entries.schema_export_media_entry)}
                       406 {:body (get-schema :media-entries-schema-schema_publish_failed)}}}}]])

;### Debug ####################################################################
;(debug/debug-ns *ns*)
