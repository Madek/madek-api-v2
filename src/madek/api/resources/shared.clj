(ns madek.api.resources.shared
  (:require [cheshire.core :as cheshire]
            [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as logging]
            [clojure.walk :refer [keywordize-keys]]
            [java-time.api :as jt]
            [compojure.core :as cpj]
            [logbug.catcher :as catcher]
            [schema.core :as s]
            [madek.api.utils.sql :as sql]
            [madek.api.authorization :refer [authorized?]]
            [madek.api.semver :as semver]
            [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
            [madek.api.constants :as mc]
            ))


(def schema_ml_list
  {(s/optional-key :de) (s/maybe s/Str)
   (s/optional-key :en) (s/maybe s/Str)})

(defn transform_ml [hashMap]
  "Builds Map with keys as keywords and values from HashMap (sql-hstore)"
  (if (nil? hashMap)
    nil
    (keywordize-keys (zipmap (.keySet hashMap) (.values hashMap)))))

; begin db-helpers
; TODO move to sql file
; TODO sql injection protection
(defn build-query-base [table-key col-keys]
  (-> (sql/select col-keys)
      (sql/from table-key)))

(defn build-query-param [query query-params param]
  (let [pval (-> query-params param mc/presence)]
    (if (nil? pval)
      query
      (-> query (sql/merge-where [:= param pval])))))

(defn try-parse-date-time [dt_string]
  (try

    (let [zoneid (java.time.ZoneId/systemDefault)
          parsed2 (jt/local-date-time (jt/offset-date-time dt_string) zoneid)
          ;parsed (or
          ;        (try (.atZoneSameInstant (java.time.OffsetDateTime/parse dt_string) zoneid)
          ;             (catch Exception ex nil))
          ;        (try (java.time.ZonedDateTime/of (java.time.LocalDateTime/parse dt_string) zoneid)
          ;             (catch Exception ex nil)))

          ;local (.toLocalDateTime zoned)
          ;local (.toLocalDateTime parsed)
          pcas (.toString parsed2)]
      (logging/info "try-parse-date-time "
                    dt_string
                    "\n zoneid " zoneid
                    "\n parsed2 " parsed2
                    ;"\n zoned " zoned
                    ;"\n local " local
                    "\n pcas " pcas)
      pcas)

    (catch Exception ex
      (logging/error "Invalid date time string" (ex-message ex))
      nil)))


(defn build-query-ts-after [query query-params param col-name]
  (let [pval (-> query-params param mc/presence)]
    (if (nil? pval)
      query
      (let [parsed (try-parse-date-time pval)]
        (if (nil? parsed)
          query
          (-> query (sql/merge-where 
                     (sql/raw (str "'" parsed "'::timestamp < " col-name)))))))))


(defn build-query-created-or-updated-after [query query-params param]
  (let [pval (-> query-params param mc/presence)]
    (if (nil? pval)
      query
      (let [parsed (try-parse-date-time pval)]
        (if (nil? parsed)
          query
          (-> query (sql/merge-where [:or
                                      (sql/raw (str "'" parsed "'::timestamp < created_at"))
                                      (sql/raw (str "'" parsed "'::timestamp < updated_at"))]))
          )
      )
    )
  ))

(defn build-query-param-like [query query-params param]
  (let [pval (-> query-params param mc/presence)
        qval (str "%" pval "%")]
    (if (nil? pval)
      query
      (-> query (sql/merge-where [:like param qval])))))


(defn- sql-query-find-eq
  ([table-name col-name row-data]
   (-> (build-query-base table-name :*)
       (sql/merge-where [:= col-name row-data])
       sql/format))
  ([table-name col-name row-data col-name2 row-data2]
   (-> (build-query-base table-name :*)
       (sql/merge-where [:= col-name row-data])
       (sql/merge-where [:= col-name2 row-data2])
       sql/format))
  )


(defn sql-update-clause
  "Generates an sql update clause"
  ([col-name row-data]
  [(str col-name " = ?") row-data])
  ([col-name row-data col-name2 row-data2]
   [(str col-name " = ? AND " col-name2 " = ? ") row-data row-data2])
  ([col-name row-data col-name2 row-data2 col-name3 row-data3]
   [(str col-name " = ? AND " col-name2 " = ? AND " col-name3 " = ? ") row-data row-data2 row-data3])
  )

(defn hsql-upd-clause-format
  "Transforms honey sql to sql update clause"
  [sql-cls]
  (update-in sql-cls [0] #(clojure.string/replace % "WHERE" "")))

(defn query-find-all
  [table-key col-keys]
  (let [db-query (-> (build-query-base table-key col-keys)
                     sql/format)
        db-result (jdbc/query (get-ds) db-query)
        ]
    ;(logging/info "query-find-all" "\ndb-query\n" db-query "\ndb-result\n" db-result)
    db-result))

(defn query-eq-find-all [table-name col-name row-data] 
  (catcher/snatch {}
                  (jdbc/query
                   (get-ds)
                   (sql-query-find-eq table-name col-name row-data))))

(defn query-eq-find-one [table-name col-name row-data]
  (first (query-eq-find-all table-name col-name row-data)))

(defn query-eq2-find-all [table-name col-name row-data col-name2 row-data2]
  (catcher/snatch {}
                  (jdbc/query
                   (get-ds)
                   (sql-query-find-eq table-name col-name row-data col-name2 row-data2))))

(defn query-eq2-find-one [table-name col-name row-data col-name2 row-data2]
  (first (query-eq2-find-all table-name col-name row-data col-name2 row-data2)))

; end db-helpers

; begin request/response/utils helpers

;(def uuid-matcher #"[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}" )

(def internal-keys [:admin_comment])

(defn remove-internal-keys
  [resource]
  (apply dissoc resource internal-keys))


(defn response_ok 
  ([msg] (response_ok msg 200))
  ([msg status] {:status status :body msg})
  )

(defn response_failed
  ([msg status] {:status status :body {:message msg}}))


(defn response_not_found [msg]
  {:status 404 :body {:message msg}})

(defn response_exception [ex]
  (merge (ex-data ex) {:status 500
                      :body {:message (.getMessage ex)}}))

(def root
  {:status 200
   :body {:api-version (semver/get-semver)
          :message "Hello Madek User!"}})

(def no_impl
  {:status 501
   :body {:api-version (semver/get-semver)
          :message "Not Implemented! TODO!"}})

(defn show-params [req]
  {:status 200
   :body {:params (-> req :params)
          :parameters (-> req :parameters)
          :query-params (-> req :query-params)
          :query (-> req :query)
          :headers (-> req :headers)}})

; end request response helpers

; log helper
(defn logwrite
  "Logs requests authed user id "
  [request msg] 
  (if-let [auth-id (-> request :authenticated-entity :id)]
    (logging/info "WRITE: User: " auth-id "; Message: " msg)
    (logging/info "WRITE: anonymous; Message: " msg)))
  
  ;([auth-entity msg entity]
  ; (logging/info
  ;  "WRITE: "
  ;  (if (nil? auth-entity)
  ;    "anonymous; "
  ;    (str "user: " (:id auth-entity) "; "))
  ;  "E: " entity
  ;  "M: " msg)))


; begin generic path param find in db and assoc with request

(defn req-find-data
  "Extracts requests path-param, searches on db_table in col_name for its value.
   It does send404 if set true and no such entity is found.
   If it exists it is associated with the request as reqkey"
  [request handler path-param db_table db_col_name reqkey send404]
  (let [search (-> request :parameters :path path-param)]
    ;(logging/info "req-find-data: " search " " db_table " " db_col_name)
    (if-let [result-db (query-eq-find-one db_table db_col_name search)]
      (handler (assoc request reqkey result-db))
      (if (= true send404)
        (response_not_found (str "No such entity in " db_table " as " db_col_name " with " search))
        (handler request)))))

(defn req-find-data-search2
  "Searches on db_table in col_name/2 for values search and search2.
   It does send404 if set true and no such entity is found.
   If it exists it is associated with the request as reqkey"
  [request handler search search2 db_table db_col_name db_col_name2 reqkey send404]
    (logging/info "req-find-data-search2" "\nc1: " db_col_name "\ns1: " search "\nc2: " db_col_name2 "\ns2: " search2)
    (if-let [result-db (query-eq2-find-one db_table db_col_name search db_col_name2 search2)]
      (handler (assoc request reqkey result-db))
      (if (= true send404)
        (response_not_found (str "No such entity in " db_table " as " db_col_name " with " search " and " db_col_name2 " with " search2))
        (handler request))))


(defn req-find-data2
  "Extracts requests path-params (1/2),
   searches on db_table in col_names (1/2) for its value.
   It does send404 if set true and no such entity is found.
   If it exists it is associated with the request as reqkey"
  [request handler path-param path-param2 db_table db_col_name db_col_name2 reqkey send404]
  (let [search (-> request :parameters :path path-param str)
        search2 (-> request :parameters :path path-param2 str)]
    
    ;(logging/info "req-find-data2" "\nc1: " db_col_name "\ns1: " search "\nc2: " db_col_name2 "\ns2: " search2)
    (if-let [result-db (query-eq2-find-one db_table db_col_name search db_col_name2 search2)]
      (handler (assoc request reqkey result-db))
      (if (= true send404)
        (response_not_found (str "No such entity in " db_table " as " db_col_name " with " search " and " db_col_name2 " with " search2))
        (handler request)))))


; end generic path param find in db and assoc with request

; begin user and other util wrappers

(defn is-admin [user-id]
  (let [none (->
              (jdbc/query
               (get-ds)
               ["SELECT * FROM admins WHERE user_id = ? " user-id]) empty?)
        result (not none)]
    ;(logging/info "is-admin: " user-id " : " result)
    result
    ))

; end user and other util wrappers

; begin media resources helpers
(defn- get-media-resource
  "First checks for collection_id, then for media_entry_id.
   If creating collection-media-entry-arc, the collection permission is checked."
  ([request]
     (or (get-media-resource request :collection_id "collections" "Collection")
         (get-media-resource request :media_entry_id "media_entries" "MediaEntry")
         ))
   
  ([request id-key table-name type]
   (try
     (when-let [id (-> request :parameters :path id-key)]
       ;(logging/info "get-media-resource" "\nid\n" id)
       (when-let [resource (-> (jdbc/query (get-ds)
                                           [(str "SELECT * FROM " table-name "
                                               WHERE id = ?") id]) first)]
         (assoc resource :type type :table-name table-name)
         )
       )
     
     (catch Exception e 
       (logging/error "ERROR: get-media-resource: " (ex-data e))
       (merge (ex-data e) 
              {:statuc 406,:body {:message (.getMessage e)}})
       ))
   )
  
)


(defn- ring-add-media-resource [request handler]
  (if-let [media-resource (get-media-resource request)]
    (let [request-with-media-resource (assoc request :media-resource media-resource)]
      ;(logging/info "ring-add-media-resource" "\nmedia-resource\n" media-resource)
      (handler request-with-media-resource))
    {:status 404}))


; end media resources helpers

; begin meta-data helpers

(defn query-meta-datum [request]
  (let [id (or (-> request :params :meta_datum_id) (-> request :parameters :path :meta_datum_id))]
    (logging/info "query-meta-datum" "\nid\n" id)
    (or (-> (jdbc/query (get-ds)
                        [(str "SELECT * FROM meta_data "
                              "WHERE id = ? ") id])
            first)
        (throw (IllegalStateException. (str "We expected to find a MetaDatum for "
                                            id " but did not."))))))

(defn- query-media-resource-for-meta-datum [meta-datum]
  (or (when-let [id (:media_entry_id meta-datum)]
        (get-media-resource {:parameters {:path {:media_entry_id id}} }
                            :media_entry_id "media_entries" "MediaEntry"))
      (when-let [id (:collection_id meta-datum)]
        (get-media-resource {:parameters {:path {:collection_id id}}}
                            :collection_id "collections" "Collection"))
      (throw (IllegalStateException. (str "Getting the resource for "
                                          meta-datum "
                                          is not implemented yet.")))))


(defn- ring-add-meta-datum-with-media-resource [request handler]
  (if-let [meta-datum (query-meta-datum request)]
    (let [media-resource (query-media-resource-for-meta-datum meta-datum)]
      (logging/info "add-meta-datum-with-media-resource" "\nmeta-datum\n" meta-datum "\nmedia-resource\n" media-resource)
      (handler (assoc request
                      :meta-datum meta-datum
                      :media-resource media-resource)))
    (handler request)))

; end meta-data helpers

; begin media-resource auth helpers

(defn- public? [resource]
  (-> resource :get_metadata_and_previews boolean))

(defn- authorize-request-for-media-resource [request handler scope]
  ;(
   ;(logging/info "auth-request-for-mr" 
   ;              "\nscope: " scope
   ;              "\nauth entity:\n" (-> request :authenticated-entity)
   ;              "\nis-admin:\n" (-> request :is_admin)
   ;              )
   (if-let [media-resource (:media-resource request)]

     (if (and (= scope :view) (public? media-resource))
       ; viewable if public
       (handler request)

       (if-let [auth-entity (-> request :authenticated-entity)]
          (if (-> request :is_admin true?)
            ; do all as admin
            (handler request)

            ; if not admin check user auth
            (if (authorized? auth-entity media-resource scope)
              (handler request)
              ;else
              {:status 403 :body {:message "Not authorized for media-resource"}}
            )
          )

        ;else
        {:status 401 :body {:message "Not authorized"}}
       )
     )
     ; else
     (let [response  {:status 500 :body {:message "No media-resource in request."}}]
       (logging/warn 'authorize-request-for-media-resource response [request handler])
       response)
   )
  ;)
)

; end media-resource auth helpers

; begin json query param helpers

(defn try-as-json [value]
  (try (cheshire/parse-string value)
       (catch Exception _
         value)))

(defn- *ring-wrap-parse-json-query-parameters [request handler]
  ;((assoc-in request [:query-params2] (-> request :parameters :query))
  (handler (assoc request :query-params
                  (->> request :query-params
                       (map (fn [[k v]] [k (try-as-json v)]))
                       (into {})))))

; end json query param helpers

; begin wrappers

(defn ring-wrap-add-media-resource [handler]
  (fn [request]
    (ring-add-media-resource request handler)))

(defn ring-wrap-add-meta-datum-with-media-resource [handler]
  (fn [request]
    (ring-add-meta-datum-with-media-resource request handler)))

(defn ring-wrap-authorization-view [handler]
  (fn [request]
    (authorize-request-for-media-resource request handler :view)))

(defn ring-wrap-authorization-download [handler]
  (fn [request]
    (authorize-request-for-media-resource request handler :download)))

(defn ring-wrap-authorization-edit-metadata [handler]
  (fn [request]
    (authorize-request-for-media-resource request handler :edit-md)))

(defn ring-wrap-authorization-edit-permissions [handler]
  (fn [request]
    (authorize-request-for-media-resource request handler :edit-perm)))

(defn ring-wrap-parse-json-query-parameters [handler]
  (fn [request]
    (*ring-wrap-parse-json-query-parameters request handler)))

(defn wrap-check-valid-meta-key [param]
  (fn [handler]
    (fn [request]
      (let [meta-key-id (-> request :parameters :path param)]
        (if (re-find #"^[a-z0-9\-\_\:]+:[a-z0-9\-\_\:]+$" meta-key-id)
          (handler request)
          (response_failed (str "Wrong meta_key_id format! See documentation."
                                " (" meta-key-id ")") 422))))))

;end wrappers

; begin swagger docu summary helpers

(def s_cnv_acc "Convenience access.")

(defn sum_todo [text] (apply str "TODO: " text))
(defn sum_pub [text] (apply str "PUBLIC Context: " text))
(defn sum_usr [text] (apply str "USER Context: " text))
(defn sum_usr_pub [text] (apply str "PUBLIC/USER Context: " text))
(defn sum_adm [text] (apply str "ADMIN Context: " text))

(defn sum_cnv [text] (apply str text " " s_cnv_acc))

(defn sum_cnv_adm [text] (sum_adm (sum_cnv text)))

(defn sum_adm_todo [text] (sum_todo (sum_adm text)))
; end swagger docu summary helpers