(ns madek.api.resources.shared
  (:require [cheshire.core :as cheshire]
            [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as logging]
            [compojure.core :as cpj]
            [logbug.catcher :as catcher]
            [madek.api.utils.sql :as sql]
            [madek.api.authorization :refer [authorized?]]
            [madek.api.semver :as semver]
            [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
            [madek.api.constants :as mc]
            ))


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
  [col-name row-data]
  [(str col-name " = ?") row-data]
  )

(defn hsql-upd-clause-format [sql-cls]
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
  ; we wrap this since badly formated media-file-id strings can cause an
  ; exception, note that 404 is in that case a correct response
  (catcher/snatch {}
                  (jdbc/query
                   (get-ds)
                   (sql-query-find-eq table-name col-name row-data))))

(defn query-eq-find-one [table-name col-name row-data]
  (first (query-eq-find-all table-name col-name row-data)))

(defn query-eq2-find-all [table-name col-name row-data col-name2 row-data2]
  ; we wrap this since badly formated media-file-id strings can cause an
  ; exception, note that 404 is in that case a correct response
  (catcher/snatch {}
                  (jdbc/query
                   (get-ds)
                   (sql-query-find-eq table-name col-name row-data col-name2 row-data2))))

(defn query-eq2-find-one [table-name col-name row-data col-name2 row-data2]
  (first (query-eq2-find-all table-name col-name row-data col-name2 row-data2)))

; end db-helpers

; begin request response helpers

(def uuid-matcher #"[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}" )

(def dead-end-handler
  (cpj/routes
    (cpj/GET "*" _ {:status 404 :body {:message "404 NOT FOUND"}})
    (cpj/ANY "*" _ {:status 501 :body {:message "501 NOT IMPLEMENTED"}})
    ))

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

; begin generic path param find in db and assoc with request
(defn req-find-data
  [request handler path-param db_table db_col_name reqkey send404]
  (let [search (-> request :parameters :path path-param)]
    (if-let [result-db (query-eq-find-one db_table db_col_name search)]
      (handler (assoc request reqkey result-db))
      (if (= true send404)
        (response_not_found (str "No such entity in " db_table " as " db_col_name " with " search))
        (handler request)))))

(defn req-find-data-search2
  [request handler search search2 db_table db_col_name db_col_name2 reqkey send404]
    ;(logging/info "req-find-data-search2" "\nc1: " db_col_name "\ns1: " search "\nc2: " db_col_name2 "\ns2: " search2)
    (if-let [result-db (query-eq2-find-one db_table db_col_name search db_col_name2 search2)]
      (handler (assoc request reqkey result-db))
      (if (= true send404)
        (response_not_found (str "No such entity in " db_table " as " db_col_name " with " search " and " db_col_name2 " with " search2))
        (handler request))))


(defn req-find-data2
  [request handler path-param path-param2 db_table db_col_name db_col_name2 reqkey send404]
  (let [search (-> request :parameters :path path-param str)
        search2 (-> request :parameters :path path-param2 str)]
    
    (logging/info "req-find-data2" "\nc1: " db_col_name "\ns1: " search "\nc2: " db_col_name2 "\ns2: " search2)
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
  ([request]
   (catcher/with-logging {}
     (or (get-media-resource request :media_entry_id "media_entries" "MediaEntry")
         (get-media-resource request :collection_id "collections" "Collection"))))
  ([request id-key table-name type]
   (when-let [id (or (-> request :params id-key) (-> request :parameters :path id-key))]
     (logging/info "get-media-resource" "\nid\n" id)
     (when-let [resource (-> (jdbc/query (get-ds)
                                         [(str "SELECT * FROM " table-name "
                                               WHERE id = ?") id]) first)]
       (assoc resource :type type :table-name table-name)))))


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
        (get-media-resource {:params {:media_entry_id id}}
                            :media_entry_id "media_entries" "MediaEntry"))
      (when-let [id (:collection_id meta-datum)]
        (get-media-resource {:params {:collection_id id}}
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
  ;((logging/info "auth-request-for-mr" "\nscope: " scope)
   (if-let [media-resource (:media-resource request)]
         
     (if (and (= scope :view) (public? media-resource))
       ; viewable if public
       (handler request)

       ;((logging/info "check auth" 
        ;              "\nae\n" (-> request :authenticated-entity)
        ;              "\nia\n" (-> request :is-admin))
        
        (if-let [auth-entity (-> request :authenticated-entity)]
         (if (-> request :is-admin true?)
          ; do all as admin
           ;((logging/info "do as admin")
            (handler request)
           ;)
          ; check user auth
           (if (authorized? auth-entity media-resource scope)
             (handler request)
             {:status 403 :body {:message "Not authorized for media-resource"}}))

         {:status 401 :body {:message "Not authorized"}})
        ;)
       )
    (let [response  {:status 500 :body {:message "No media-resource in request."}}]
      (logging/warn 'authorize-request-for-media-resource response [request handler])
      response)))

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

;end wrappers

; begin swagger docu summary helpers

(def s_cnv_acc "Convenience access.")

(defn sum_todo [text] (apply str "TODO: " text))
(defn sum_pub [text] (apply str "PUBLIC Context: " text))
(defn sum_usr [text] (apply str "USER Context: " text))
(defn sum_adm [text] (apply str "ADMIN Context: " text))

(defn sum_cnv [text] (apply str text " " s_cnv_acc))

(defn sum_cnv_adm [text] (sum_adm (sum_cnv text)))

(defn sum_adm_todo [text] (sum_todo (sum_adm text)))
; end swagger docu summary helpers