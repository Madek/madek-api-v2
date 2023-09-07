(ns madek.api.resources.media-entries
  (:require
    [clojure.java.io :as io]
   [clojure.java.jdbc :as jdbc]
   [clojure.tools.logging :as logging]
   [madek.api.resources.app-settings :refer [db_get-app-settings]]
   [madek.api.resources.media-entries.index :refer [get-index, get-index_related_data]]
   [madek.api.resources.media-entries.media-entry :refer [get-media-entry]]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.rdbms :as rdbms]
   [reitit.coercion.schema]
   [reitit.coercion.spec]
   [clojure.spec.alpha :as sa]
   [reitit.ring.middleware.multipart :as multipart]
   [schema.core :as s]
   ;[pantomime.mime :refer [mime-type-of]]
   
   [madek.api.resources.app-settings :as app-settings]
   [madek.api.resources.context-keys :as context_keys]
   [madek.api.authorization :as authorization])
  )

(defn handle_query_media_entry [req]
  (get-index req))

(defn handle_query_media_entry-related-data [req] 
  (get-index_related_data req))

(defn handle_get-media-entry [req]
  (let [query-params (-> req :parameters :query)
        qreq (assoc-in req [:query-params] query-params)]
    (get-media-entry qreq)
  ))

; TODO try catch
(defn handle_delete_media_entry [req]
  (let [eid (-> req :parameters :path :media_entry_id)
        
        mr (-> req :media-resource)
        fclause [ "media_entry_id = ?" eid]
        fresult (jdbc/delete! (rdbms/get-ds) :media_files fclause)
        dclause ["id = ?" eid]
        dresult (jdbc/delete! (rdbms/get-ds) :media_entries dclause)
        ]
    (logging/info "handle_delete_media_entry"
                  "\n eid: \n" eid
                  "\n fclause: \n" fclause
                  "\n fresult: \n" fresult
                  "\n dclause: \n" dclause
                  "\n dresult: \n" dresult
                  )
    (if (= 1 (first dresult))
      (sd/response_ok {:deleted mr})
      (sd/response_failed {:message "Failed to delete media entry"} 406))
    ))

(defn- get-context-keys-4-context [contextId]
  (map :meta_key_id
       (sd/query-eq-find-all :context_keys :context_id contextId)
       ))

(defn- check-has-meta-data-for-context-key [meId mkId]
 (let [md (sd/query-eq2-find-one :meta_data :media_entry_id (str meId) :meta_key_id mkId)
       hasMD (not (nil? md))
       result {(keyword mkId) hasMD}]
   ;(logging/info "check-has-meta-data-for-context-key:" meId  ":"  result)
   result
   ))

(defn handle_try-publish-media-entry [req]
  "Checks all Contexts in AppSettings-contexts_for_entry_validation.
   All the meta-data for the meta-keys have to be set.
   In that case, the is_publishable of the entry is set to true."
  (let [eid (-> req :parameters :path :media_entry_id)
        mr (-> req :media-resource)
        
        validationContexts (-> (sd/query-find-all :app_settings :contexts_for_entry_validation)
                               first
                               :contexts_for_entry_validation)
        contextKeys (first (map get-context-keys-4-context validationContexts))
        hasMetaData (for [cks contextKeys]
                      (check-has-meta-data-for-context-key eid cks))
        tf (for [elem hasMetaData] (vals elem) )
        publishable (reduce (fn [tfresult tfval] (and tfresult (first tfval))) [true] tf)
        ]
    
    (logging/info "handle_try-publish-media-entry"
                  "\n eid: \n" eid
                  "\n validationContexts: \n" validationContexts
                  "\n contextKeys: \n" contextKeys
                  "\n hasMetaData: \n" hasMetaData
                  "\n tf: \n" tf
                  "\n publishable: \n" publishable
                  )
    (if (true? publishable)
      (let [data {:is_published true}
            dresult (jdbc/update! (rdbms/get-ds) :media_entries data ["id = ?" eid])]
        
        (logging/info "handle_try-publish-media-entry"
                      "\n published: entry_id: \n" eid
                      "\n dresult: \n" dresult)
        
        (if (= 1 (first dresult))
          (sd/response_ok (sd/query-eq-find-one :media_entries :id eid))
          (sd/response_failed "Could not update publish on media_entry." 406)
        )
      )

      (sd/response_failed {:is_publishable publishable
                           :media_entry_id eid 
                           :has_meta_data hasMetaData} 406)
    )))


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
   :access_hash (clj-uuid/v4)
   })

(def MC-FILE_STORAGE_DIR "tmp/originals")

(defn original-store-location [mf]
  (let [guid (:guid mf)
        loc (apply str MC-FILE_STORAGE_DIR "/" (first guid) "/" guid)]
    ;(logging/info "\nstore-location\n" "\nmf\n" mf "\nguid\n" guid "\nloc\n" loc)
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
    ;    (logging/info "handle_uploaded_file_resp_ok: added to collection: " collection-id "\nresult\n" add-col-res)
    ;    (logging/error "Failed: handle_uploaded_file_resp_ok: add to collection: " collection-id))
    ;    (sd/response_ok {:media_entry (assoc media-entry :media_file media-file :collection_id collection-id)})
    ;    (sd/response_ok {:media_entry (assoc media-entry :media_file media-file)}))
      
  ;))
    
    


(defn create-media_entry
  "Only for testing. Does not trigger media convert. So previews are missing."
  [file auth-entity mime collection-id]
  (let [user-id (:id auth-entity)
        new-me {:responsible_user_id (str user-id)
                :creator_id (str user-id)
                :is_published false}
        ]
    ; TODO authorize me
    ; handle workflow authorize 

    (jdbc/with-db-transaction [tx (rdbms/get-ds)]
      (if-let [new-mer (first (jdbc/insert! tx "media_entries" new-me))]
        (let [me-id (:id new-mer)
              mf (new_media_file_attributes file user-id mime)
              new-mf (assoc mf :media_entry_id me-id)]
          
          (logging/info "\ncreate-me: " "\ncreated media-entry: " new-mer "\nnew media-file: " new-mf)

          (if-let [new-mfr (first (jdbc/insert! tx "media_files" new-mf))]
            (handle_uploaded_file_resp_ok file new-mfr new-mer collection-id tx)
            (sd/response_failed "Could not create media-file" 406)
            )
        )
        (sd/response_failed "Could not create media-entry" 406)
      )
    )

    ))



; TODO try catch
; use redirect to web-app
(defn handle_create-media-entry [req]
  (let [copy-md-id (-> req :parameters :query :copy_me_id)
        collection-id (-> req :parameters :query :collection_id)
        ; TODO collection id
        file (-> req :parameters :multipart :file)
        file-content-type (-> file :content-type)
        temppath (.getPath (:tempfile file))
        auth (-> req :authenticated-entity)]

    (logging/info "handle_create-media-entry"
                  "\nauth\n" (:id auth)
                  "\ncopy_md\n" copy-md-id
                  "\ncollection-id\n" collection-id
                  "\nfile\n" file
                  "\n content: " file-content-type
                  "\ntemppath\n" temppath)

    (let [;mime (or file-content-type (mime-type-of temppath) )
          mime file-content-type]
          
      (logging/info "handle_create-media-entry" "\nmime-type\n" mime)
      (if (nil? auth)
        (sd/response_failed "Not authed" 406)
        ; TODO move response handling here
        ; TODO move coll create here
        (create-media_entry file auth mime collection-id)))))

;(when-let [mime (mime-type-of tempfile)]
       ;  (sd/response_ok {:file file :mime-type mime}))
       ;(sd/response_ok {:file file :mime-type "none"}))
    ;(mime-type-of (java.io.File. "some/file/without/extension"))



(def schema_query_media_entries 
  {(s/optional-key :collection_id) s/Uuid
   (s/optional-key :order) (s/enum "desc" "asc" "title_asc" "title_desc" "last_change" "manual_asc" "manual_desc" "stored_in_collection")
   (s/optional-key :filter_by) s/Str
   ;(s/optional-key :filter_by)
   ; {
   ;  (s/optional-key :media_entry) {:is_published s/Bool
   ;                                 :creator_id s/Uuid
   ;                                 :responsible_user_id s/Uuid
   ;                                 }
   ;  (s/optional-key :media_files) {:keys :values}
   ;  (s/optional-key :permissions) {(s/optional-key :public) s/Bool
   ;                                 (s/optional-key :responsible_user) s/Uuid
   ;                                 (s/optional-key :entrusted_to_user) s/Uuid
   ;                                }
   ;  (s/optional-key :meta_data) [{(s/optional-key :type) s/Str
   ;                               (s/optional-key :key) s/Str
   ;                               (s/optional-key :match) s/Str
   ;                               (s/optional-key :value) s/Str}]
   ; TODO docu
   ;  (s/optional-key :search) s/Str
   ;  }

   (s/optional-key :me_get_metadata_and_previews) s/Bool
   (s/optional-key :me_get_full_size) s/Bool
   (s/optional-key :public_get_metadata_and_previews) s/Bool
   (s/optional-key :public_get_full_size) s/Bool
   (s/optional-key :page) s/Int
   (s/optional-key :count) s/Int
   (s/optional-key :with_full_data) s/Bool
   })

(def schema_export_media_entry
  {
   :id s/Uuid
   :creator_id s/Uuid
   :responsible_user_id s/Uuid
   :get_full_size s/Bool
   :get_metadata_and_previews s/Bool
   :is_published s/Bool

   :created_at s/Any
   :updated_at s/Any
   
   :edit_session_updated_at s/Any
   :meta_data_updated_at s/Any

   ;:responsible_delegation_id (s/maybe s/Uuid)
  })

(def schema_export_col_arc
  {:media_entry_id s/Uuid
   :id s/Uuid
   :order (s/maybe s/Str)
   :position s/Int
   :created_at s/Any
   :updated_at s/Any})



(def schema_query_media_entries_result
  {:media_entries [ schema_export_media_entry]
   (s/optional-key :col_arcs) [schema_export_col_arc]})

(def schema_export_media_file
  {
   :id s/Uuid
   :media_entry_id s/Uuid
   :conversion_profiles [ s/Any ]
   :media_type (s/maybe s/Str) ; TODO enum
   :width (s/maybe s/Int)
   :height (s/maybe s/Int)
   :meta_data (s/maybe s/Str)
   :size s/Int
   :uploader_id s/Uuid
   :content_type s/Str
   :access_hash s/Str
   :extension s/Str
   :filename s/Str
   :guid s/Str
   :updated_at s/Any
   :created_at s/Any
  })

(def schema_export_preview
  { })

(def schema_export_meta_data
  {:id s/Uuid
   :media_entry_id (s/maybe s/Uuid)
   :collection_id (s/maybe s/Uuid)

   :type s/Str
   :meta_key_id s/Str
   :string (s/maybe s/Str)
   :json (s/maybe s/Str)

   :meta_data_updated_at s/Any
   :other_media_entry_id (s/maybe s/Uuid)})

(def schema_query_media_entries_related_result
  {:media_entries [schema_export_media_entry]
   :meta_data [ [ schema_export_meta_data]]
   :media_files [[ schema_export_media_file ]]
   :previews [[ s/Any]] ; schema_export_preview]]
   (s/optional-key :col_arcs) [schema_export_col_arc]
   (s/optional-key :col_meta_data) [schema_export_meta_data]})


(def schema_publish_failed
  {:message {:is_publishable s/Bool
             :media_entry_id s/Uuid
             :has_meta_data [{s/Any s/Bool}]}})

(def ring-routes 
  ["/"
   ["media-entries"
    {:get
     {:summary "Query media-entries."
      :swagger {:produces "application/json"}
      :content-type "application/json"
      :handler handle_query_media_entry
      :middleware [sd/ring-wrap-parse-json-query-parameters]
      :coercion reitit.coercion.schema/coercion
      :parameters {:query schema_query_media_entries}
      :responses { 200 { :body schema_query_media_entries_result}}}}
   ]
   ["media-entries-related-data"
    {:get
     {:summary "Query media-entries with all related data."
      :swagger {:produces "application/json"}
      :content-type "application/json"
      :handler handle_query_media_entry-related-data
      :middleware [sd/ring-wrap-parse-json-query-parameters]
      :coercion reitit.coercion.schema/coercion
      :parameters {:query schema_query_media_entries}
      :responses { 200 { :body schema_query_media_entries_related_result}}}}
    ]
  ])

(sa/def ::copy_me_id string?)
(sa/def ::collection_id string?)
(def media-entry-routes
  [
   ["/media-entry"
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
   
   ["/media-entry/:media_entry_id"
    {:get {:summary "Get media-entry for id."
           :handler handle_get-media-entry
           :swagger {:produces "application/json"}
           :content-type "application/json"

           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-view]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Uuid}}
           }
     

     ; TODO Frage: wer kann einen Eintrag löschen
     :delete {:summary "Delete media-entry for id."
              :handler handle_delete_media_entry
              :swagger {:produces "application/json"}
              :content-type "application/json"
              
              :middleware [sd/ring-wrap-add-media-resource
                           sd/ring-wrap-authorization-edit-permissions]
              :coercion reitit.coercion.schema/coercion
              :parameters {:path {:media_entry_id s/Uuid}}
              }
     }
    ]
   
   ["/media-entry/:media_entry_id/publish"
    {:put {:summary "Try publish media-entry for id."
           :handler handle_try-publish-media-entry
           :swagger {:produces "application/json"}
           :content-type "application/json"
   
           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization-edit-metadata]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Uuid}}
           :responses { 200 {:body schema_export_media_entry}
                        406 {:body schema_publish_failed}}}}]
   ]
  )

;### Debug ####################################################################
;(debug/debug-ns *ns*)
