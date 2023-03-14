(ns madek.api.resources.media-entries
  (:require
    [clojure.java.io :as io]
   [clojure.java.jdbc :as jdbc]
   [clojure.tools.logging :as logging]
   [compojure.core :as cpj]
   [madek.api.resources.media-entries.index :refer [get-index]]
   [madek.api.resources.media-entries.media-entry :refer [get-media-entry]]
   [madek.api.resources.shared :as sd]
   [madek.api.utils.rdbms :as rdbms]
   [reitit.coercion.schema]
   [reitit.coercion.spec]
   [clojure.spec.alpha :as sa]
   [reitit.ring.middleware.multipart :as multipart]
   [schema.core :as s]
   [pantomime.mime :refer [mime-type-of]]
   [madek.api.resources.collection-media-entry-arcs :as collection-media-entry-arcs]
   )
  )


(def routes
  (cpj/routes
    (cpj/GET "/media-entries/" _ get-index)
    (cpj/GET "/media-entries/:id" _ get-media-entry)
    (cpj/ANY "*" _ sd/dead-end-handler)
    ))


(defn handle_get-index [req]
  (let [query-params (-> req :parameters :query)
        q1 (-> req :query-params)
        qreq (assoc-in req [:query-params] query-params)
        q2 (-> qreq :query-params)
        
        ]
    (logging/info "handle_get-index" "\nquery\n" query-params "\nq1\n" q1 "\nq2\n" q2 )
    (get-index qreq)))

(defn handle_get-media-entry [req]
  (let [query-params (-> req :parameters :query)
        qreq (assoc-in req [:query-params] query-params)]
    (get-media-entry qreq)
  ))

(def Madek-Constants-Default-Mime-Type "application/octet-stream")

(defn extract-extension [filename]
  ;(. java.io.File (getFileExtension filename))
  (let [match (re-find #"\.[A-Za-z0-9]+$" filename )]
    ;(logging/info "xtract-x: " filename "\nmatch\n " match)
    match)
  )

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
    (logging/info "\nstore-location\n"
                  "\nmf\n" mf
                  "\nguid\n" guid
                  "\nloc\n" loc)
    loc
    
    )
  )
;  def original_store_location
;path = File.join (Madek::Constants::FILE_STORAGE_DIR, guid.first, guid)
;path_for_env (path)
;end

;def thumbnail_store_location
;path = File.join (Madek::Constants::THUMBNAIL_STORAGE_DIR, guid.first, guid)
;path_for_env (path)
;end


(defn handle_uploaded_file_resp_ok
  [file media-file media-entry collection-id tx]
  (let [temp-file (-> file :tempfile)
        temp-path (.getPath temp-file)
        store-location (original-store-location media-file)]
    ; copy file
    (io/copy (io/file temp-path) (io/file store-location))
    ; return nested result data
    
    ; TODO extra data for me
    ;(me_add-default-license new-mer)
    ;(me_exract-and-store-metadata new-mer)
    ;(me_add-to-collection new-mer (or col_id_param (-> workflow :master_collection :id)))
    ;(if-let [collection (sd/query-eq-find-one "collections" "id" collection-id)]
    ;  (if-let [add-col-res (collection-media-entry-arcs/create-col-me-arc collection-id (:id media-entry) {} tx)]
    ;    (logging/info "handle_uploaded_file_resp_ok: added to collection: " collection-id "\nresult\n" add-col-res)
    ;    (logging/error "Failed: handle_uploaded_file_resp_ok: add to collection: " collection-id))
    ;    (sd/response_ok {:media_entry (assoc media-entry :media_file media-file :collection_id collection-id)})
    ;    (sd/response_ok {:media_entry (assoc media-entry :media_file media-file)}))
      (sd/response_ok {:media_entry (assoc media-entry :media_file media-file)})))
  ;))
    
    

; TODO find keys and add
; TODO keywords and meta-data db access
(defn me_add-default-license [media-entry]
   (
    ; get from app setting 
    ; license getKeyword settings.media_entry_default_license_id
    ; lic-meta-key getMetaKey settings.media_entry_default_license_meta_key
    ; create-meta-datum me lic-meta-key.id license.id
    ; get from app setting 
    ; usage_text getKeyword settings.media_entry_default_license_usage_text.presence
    ; use-meta-key getMetaKey settings.media_entry_default_license_usage_meta_key meta_datum_object_type 'MetaDatum::Text'
    ; create-meta-datum me use-meta-key.id usage_text
   ))

(defn create-media_entry
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



; TODO 
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

    (let [mime (or file-content-type (mime-type-of temppath) )]
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


(def ring-routes 
  ["/media-entries"
   ["/" 
    {:get {:summary "Get list media-entries."
           :swagger {:produces "application/json"}
           :content-type "application/json"
           :handler handle_get-index
           :middleware [sd/ring-wrap-parse-json-query-parameters]
           :coercion reitit.coercion.schema/coercion
           :parameters {:query {(s/optional-key :collection_id) s/Str
                                (s/optional-key :order) s/Any ;(s/enum "desc" "asc" "title_asc" "title_desc" "last_change" "manual_asc" "manual_desc" "stored_in_collection")
                                (s/optional-key :filter_by) s/Any
                                (s/optional-key :me_get_metadata_and_previews) s/Bool
                                (s/optional-key :me_get_full_size) s/Bool
                                (s/optional-key :page) s/Str}}}
         ; TODO
     :post {:summary (sd/sum_todo "Create media-entries.")
            :handler handle_create-media-entry
            :swagger {:consumes "multipart/form-data"}
           ;:middleware [authentication/wrap]
           ;:coercion reitit.coercion.schema/coercion
            :coercion reitit.coercion.spec/coercion
            :parameters {:query {:copy_me_id string?}
                         :multipart {:file multipart/temp-file-part}
                         }}
         }
    ]

   
   
   ["/:media_entry_id"
    {:get {:summary "Get media-entry for id."
           :handler handle_get-media-entry
           :swagger {:produces "application/json"}
           :content-type "application/json"

           :middleware [sd/ring-wrap-add-media-resource
                        sd/ring-wrap-authorization]
           :coercion reitit.coercion.schema/coercion
           :parameters {:path {:media_entry_id s/Str}}}
     
    }]])

(sa/def ::copy_me_id string?)
(sa/def ::collection_id string?)
(def media-entry-routes
  ["/media-entry/"
   {:post {:summary (sd/sum_todo "Create media-entry.")
           :handler handle_create-media-entry
           :swagger {:consumes "multipart/form-data"
                     :produces "application/json"}
           :content-type "application/json"
           :accept "multipart/form-data"
           ;:middleware [authentication/wrap]
           ; cannot use schema, need to use spec for multiplart
           ;:coercion reitit.coercion.schema/coercion
           :coercion reitit.coercion.spec/coercion
           :parameters {:query (sa/keys :opt-un [::copy_me_id ::collection_id])
                        :multipart {:file multipart/temp-file-part}}}}])
;### Debug ####################################################################
;(debug/debug-ns *ns*)
