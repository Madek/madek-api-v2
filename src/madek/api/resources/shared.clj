(ns madek.api.resources.shared
  (:require [cheshire.core :as cheshire]
            [clojure.string :as str]
            [clojure.walk :refer [keywordize-keys]]
            [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]
            [java-time.api :as jt]
            [logbug.catcher :as catcher]
            [madek.api.authorization :refer [authorized?]]
            [madek.api.constants :as mc]
            [madek.api.semver :as semver]
            [madek.api.utils.helper :refer [to-uuid]]
            [next.jdbc :as jdbc]
            [schema.core :as s]
            [taoensso.timbre :refer [error info warn]]))

(defn transform_ml [hashMap]
  "Builds Map with keys as keywords and values from HashMap (sql-hstore)"
  (if (nil? hashMap)
    nil
    (keywordize-keys (zipmap (.keySet hashMap) (.values hashMap)))))

(defn generate-swagger-pagination-params []
  {:produces "application/json"
   :parameters [{:name "page"
                 :in "query"
                 :description "Page number, defaults to 1"
                 :required true
                 :value 1
                 :default 1
                 :type "number"
                 :pattern "^[1-9][0-9]*$"}
                {:name "count"
                 :in "query"
                 :description "Number of items per page, defaults to 100"
                 :required true
                 :value 100
                 :default 100
                 :type "number"
                 :pattern "^[1-9][0-9]*$"}]})

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
      (-> query (sql/where [:= param pval])))))

(defn try-instant-on-presence [data keyword]
  (try
    ;(info "try-instant-on-presence " data keyword)
    (if-not (nil? (-> data keyword))
      (assoc data keyword (-> data keyword .toInstant))
      data)
    (catch Exception ex
      (error "Invalid instant data" (ex-message ex))
      data)))

(defn try-instant [dinst]
  (try
    ;(info "try-instant " dinst)
    (if-not (nil? dinst)
      (.toInstant dinst)
      nil)
    (catch Exception ex
      (error "Invalid instant data" dinst (ex-message ex))
      nil)))

(defn try-parse-date-time [dt_string]
  (try
    (info "try-parse-date-time "
          dt_string)
    (let [zoneid (java.time.ZoneId/systemDefault)

          parsed2 (jt/local-date-time (jt/offset-date-time dt_string) zoneid)
          pcas (.toString parsed2)]
      (info "try-parse-date-time "
            dt_string
            "\n zoneid " zoneid
            "\n parsed " parsed2
            "\n result:  " pcas)
      pcas)

    (catch Exception ex
      (error "Invalid date time string" (ex-message ex))
      nil)))

(defn build-query-ts-after [query query-params param col-name]
  (let [pval (-> query-params param mc/presence)]
    (if (nil? pval)
      query
      ;(let [parsed (try-parse-date-time pval)]
      (let [parsed (try-instant pval)]
        (info "build-query-created-or-updated-after: " pval ":" parsed)
        (if (nil? parsed)
          query
          (-> query (sql/where [:raw (str "'" parsed "'::timestamp < " col-name)])))))))

(defn build-query-created-or-updated-after [query query-params param]
  (let [pval (-> query-params param mc/presence)]
    (if (nil? pval)
      query
      ;(let [parsed (try-parse-date-time pval)]
      (let [parsed (try-instant pval)]
        (info "build-query-created-or-updated-after: " pval ":" parsed)
        (if (nil? parsed)
          query
          (-> query (sql/where [:or
                                [:raw (str "'" parsed "'::timestamp < created_at")]
                                [:raw (str "'" parsed "'::timestamp < updated_at")]])))))))

; TODO use honeysql 2.x for ilike feature
(defn build-query-param-like
  ([query query-params param]
   (build-query-param-like query query-params param param))
  ([query query-params param db-param]
   (let [pval (-> query-params param mc/presence)
         qval (str "%" pval "%")]
     (if (nil? pval)
       query
       (-> query (sql/where [:like db-param qval]))))))

(defn- sql-query-find-eq
  ([table-name col-name row-data]
   (let [query (-> (build-query-base table-name :*)
                   (sql/where [:= col-name (to-uuid row-data col-name table-name)])
                   sql-format)]
     query))

  ([table-name col-name row-data col-name2 row-data2]
   (let [query (-> (build-query-base table-name :*)
                   (sql/where [:= col-name (to-uuid row-data col-name)])
                   (sql/where [:= col-name2 (to-uuid row-data2 col-name2)])
                   sql-format)] query)))

(defn sql-update-clause
  "Generates an sql update clause"
  ([col-name row-data]
   [(str col-name " = ?") row-data])
  ([col-name row-data col-name2 row-data2]
   [(str col-name " = ? AND " col-name2 " = ? ") row-data row-data2])
  ([col-name row-data col-name2 row-data2 col-name3 row-data3]
   [(str col-name " = ? AND " col-name2 " = ? AND " col-name3 " = ? ") row-data row-data2 row-data3]))

(defn hsql-upd-clause-format
  "Transforms honey sql to sql update clause"
  [sql-cls]
  (update-in sql-cls [0] #(clojure.string/replace % "WHERE" "")))

(defn query-find-all
  [table-key col-keys tx]
  (let [db-query (-> (build-query-base table-key col-keys)
                     sql-format)
        db-result (jdbc/execute! tx db-query)]
    db-result))

(defn query-eq-find-all
  ([table-name col-name row-data tx]
   (catcher/snatch {}
                   (jdbc/execute!
                    tx
                    (sql-query-find-eq table-name col-name row-data))))

  ([table-name col-name row-data col-name2 row-data2 tx]
   (catcher/snatch {}
                   (jdbc/execute!
                    tx
                    (sql-query-find-eq table-name col-name row-data col-name2 row-data2)))))

(defn query-eq-find-all-one
  ([table-name col-name row-data tx]
   (catcher/snatch {}
                   (jdbc/execute-one!
                    tx
                    (sql-query-find-eq table-name col-name row-data))))

  ([table-name col-name row-data col-name2 row-data2 tx]
   (catcher/snatch {}
                   (jdbc/execute-one!
                    tx
                    (sql-query-find-eq table-name col-name row-data col-name2 row-data2)))))

(defn query-eq-find-one
  ([table-name col-name row-data tx]
   (query-eq-find-all-one table-name col-name row-data tx))
  ([table-name col-name row-data col-name2 row-data2 tx]
   (query-eq-find-all-one table-name col-name row-data col-name2 row-data2 tx)))

#_(defn query-eq2-find-all [table-name col-name row-data col-name2 row-data2 tx]
    (catcher/snatch {}
                    (jdbc/query
                     tx
                     (sql-query-find-eq table-name col-name row-data col-name2 row-data2))))

#_(defn query-eq2-find-one [table-name col-name row-data col-name2 row-data2]
    (first (query-eq-find-all table-name col-name row-data col-name2 row-data2)))

; end db-helpers

; begin request/response/utils helpers

;(def uuid-matcher #"[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}" )

(def internal-keys [:admin_comment :enabled_for_public_view :enabled_for_public_use])

(defn remove-internal-keys
  ([resource]
   (remove-internal-keys resource internal-keys))
  ([resource keys]
   (apply dissoc resource keys)))

(defn response_ok
  ([msg] (response_ok msg 200))
  ([msg status] {:status status :body msg}))

(defn response_failed
  ([] {:status 409 :body {:message "Failure occurred"}})
  ([msg status] {:status status :body {:message msg}}))

(defn response_bad_request
  ([msg]
   {:status 400
    :body {:message (str "Bad Request: " msg)}
    ;:headers {"content-type" "application/json; charset=utf-8"}
    })
  ([msg details]
   {:status 400
    :body {:message (str "Bad Request: " msg) :details details}
    ;:headers {"content-type" "application/json; charset=utf-8"}
    }))

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
    (info "WRITE: User: " auth-id "; Message: " msg)
    (info "WRITE: anonymous; Message: " msg)))

  ;([auth-entity msg entity]
  ; (info
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
  (let [search (-> request :parameters :path path-param)
        tx (:tx request)]
    ;(info "req-find-data: " search " " db_table " " db_col_name)
    (if-let [result-db (query-eq-find-one db_table db_col_name search tx)]
      (handler (assoc request reqkey result-db))
      (if (= true send404)
        (response_not_found (str "No such entity in " db_table " as " db_col_name " with " search))
        (handler request)))))

(defn req-find-data-new
  "Extracts requests path-param, searches on db_table in col_name for its value.
   It does send404 if set true and no such entity is found.
   If it exists it is associated with the request as reqkey"
  [request handler path-param db_table db_col_name reqkey send404]
  (let [search (-> request :path-params path-param)
        tx (:tx request)]
    ;(info "req-find-data: " search " " db_table " " db_col_name)
    (if-let [result-db (query-eq-find-one db_table db_col_name search tx)]
      (handler (assoc request reqkey result-db))
      (if (= true send404)
        (response_not_found (str "No such entity in " db_table " as " db_col_name " with " search))
        (handler request)))))

(defn req-find-data-search2
  "Searches on db_table in col_name/2 for values search and search2.
   It does send404 if set true and no such entity is found.
   If it exists it is associated with the request as reqkey"
  [request handler search search2 db_table db_col_name db_col_name2 reqkey send404]
  (info "req-find-data-search2" "\nc1: " db_col_name "\ns1: " search "\nc2: " db_col_name2 "\ns2: " search2)
  (if-let [result-db (query-eq-find-one db_table db_col_name search db_col_name2 search2 (:tx request))]
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
        search2 (-> request :parameters :path path-param2 str)
        tx (:tx request)
        res (query-eq-find-one db_table db_col_name search db_col_name2 search2 tx)]

;(info "req-find-data2" "\nc1: " db_col_name "\ns1: " search "\nc2: " db_col_name2 "\ns2: " search2)
    (if-let [result-db res]
      (handler (assoc request reqkey result-db))
      (if (= true send404)
        (response_not_found (str "No such entity in " db_table " as " db_col_name " with " search " and " db_col_name2 " with " search2))
        (handler request)))))

; end generic path param find in db and assoc with request

; begin user and other util wrappers

(defn is-admin [user-id tx]
  (let [none (->
              (jdbc/execute!
               tx
               (-> (sql/select :*)
                   (sql/from :admins)
                   (sql/where [:= :user_id (to-uuid user-id)])
                   sql-format))
              empty?)
        result (not none)]
    ;(info "is-admin: " user-id " : " result)
    result))

; end user and other util wrappers

; begin media resources helpers
(defn- get-media-resource
  "First checks for collection_id, then for media_entry_id.
   If creating collection-media-entry-arc, the collection permission is checked."
  ([params tx]
   (or (get-media-resource params :collection_id "collections" "Collection" tx)
       (get-media-resource params :media_entry_id "media_entries" "MediaEntry" tx)))

  ([params id-key table-name type tx]
   (try
     (when-let [id (-> params :parameters :path id-key)]
       ;(info "get-media-resource" "\nid\n" id)
       (when-let [resource (jdbc/execute-one! tx
                                              (-> (sql/select :*)
                                                  (sql/from (keyword table-name))
                                                  (sql/where [:= :id (to-uuid id)])
                                                  sql-format))]
         (assoc resource :type type :table-name table-name)))

     (catch Exception e
       (error "ERROR: get-media-resource: " (ex-data e))
       (merge (ex-data e)
              {:statuc 406, :body {:message (.getMessage e)}})))))

(defn- ring-add-media-resource [request handler tx] ;;here
  (if-let [media-resource (get-media-resource request tx)]
    (let [request-with-media-resource (assoc request :media-resource media-resource)]
      ;(info "ring-add-media-resource" "\nmedia-resource\n" media-resource)
      (handler request-with-media-resource))
    {:status 404}))

; end media resources helpers

; begin meta-data helpers

(defn query-meta-datum [request]
  (let [id (-> request :parameters :path :meta_datum_id)]
    #_(info "query-meta-datum" "\nid\n" id)
    (or
     (jdbc/execute-one! (:tx request)
                        (-> (sql/select :*)
                            (sql/from :meta_data)
                            (sql/where [:= :id (to-uuid id)])
                            sql-format))

     (throw (IllegalStateException. (str "We expected to find a MetaDatum for "
                                         id " but did not."))))))

(defn- query-media-resource-for-meta-datum [meta-datum tx]
  (or (when-let [id (:media_entry_id meta-datum)]
        (get-media-resource {:parameters {:path {:media_entry_id id}}}
                            :media_entry_id "media_entries" "MediaEntry" tx))
      (when-let [id (:collection_id meta-datum)]
        (get-media-resource {:parameters {:path {:collection_id id}}}
                            :collection_id "collections" "Collection" tx))
      (throw (IllegalStateException. (str "Getting the resource for "
                                          meta-datum "
                                          is not implemented yet.")))))

(defn- ring-add-meta-datum-with-media-resource [request handler]
  (if-let [meta-datum (query-meta-datum request)]
    (let [media-resource (query-media-resource-for-meta-datum meta-datum (:tx request))]
      ;(info "add-meta-datum-with-media-resource" "\nmeta-datum\n" meta-datum "\nmedia-resource\n" media-resource)
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
  ;(info "auth-request-for-mr"
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
          (if (authorized? auth-entity media-resource scope (:tx request))
            (handler request)
            ;else
            {:status 403 :body {:message "Not authorized for media-resource"}}))

        ;else
        {:status 401 :body {:message "Not authorized"}}))

    ; else
    (let [response {:status 500 :body {:message "No media-resource in request."}}]
      (warn 'authorize-request-for-media-resource response [request handler])
      response))
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
    (ring-add-media-resource request handler (:tx request))))

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

(defn wrap-check-valid-meta-key-new [param]
  (fn [handler]
    (fn [request]
      (let [meta-key-id (-> request :path-params param)]
        (if (:and (not (nil? meta-key-id)) (re-find #"^[a-z0-9\-\_\:]+:[a-z0-9\-\_\:]+$" meta-key-id))
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

;; TODO: no usage
(defn sum_cnv_adm [text] (sum_adm (sum_cnv text)))

(defn sum_adm_todo [text] (sum_todo (sum_adm text)))
; end swagger docu summary helpers

(defn parsed_response_exception [ex]
  (cond
    (str/includes? (ex-message ex) "duplicate key value violates unique constraint") (response_failed (str "Violation of constraint") 409)
    (str/includes? (ex-message ex) "violates not-null constraint") (response_failed (str "Violation of constraint") 403)
    (str/includes? (ex-message ex) "is still referenced from table") (response_failed (str "References still exist") 403)
    (str/includes? (ex-message ex) "already exists") (response_failed (str "Entry already exists") 409)
    (str/includes? (ex-message ex) "is not present in table \"users\"") (response_failed (str "User entry does not exist") 404)
    (str/includes? (ex-message ex) "is not present in table \"vocabularies\"") (response_failed (str "Vocabulary entry does not exist") 404)
    (str/includes? (ex-message ex) "is not present in table \"people\"") (response_failed (str "People entry does not exist") 404)
    (str/includes? (ex-message ex) "is not present in table \"groups\"") (response_failed (str "Groups entry does not exist") 404)
    (str/includes? (ex-message ex) "is not present in table \"meta_keys\"") (response_failed (str "Meta-Keys entry does not exist") 404)
    (str/includes? (ex-message ex) "insert or update on table \"collections\" violates foreign key constraint \"fk_rails_9085ae39f1\"") (response_failed (str "Workflows entry does not exist") 404)
    (str/includes? (ex-message ex) "violates foreign key constraint") (response_failed (str "Violation of constraint (specific error-handler not yet defined)") 403)
    :else (response_exception ex)))

(defn transform_ml_map [data]
  (cond-> data
    (:labels data) (assoc :labels (transform_ml (:labels data)))
    (:descriptions data) (assoc :descriptions (transform_ml (:descriptions data)))
    (:contents data) (assoc :contents (transform_ml (:contents data)))))

;(debug/debug-ns *ns*)