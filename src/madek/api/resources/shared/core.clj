(ns madek.api.resources.shared.core
  (:require [clojure.string :as str]
            [clojure.walk :refer [keywordize-keys]]
            [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]
            [madek.api.resources.shared.db_helper :as dbh]
            [madek.api.semver :as semver]
            [madek.api.utils.helper :refer [to-uuid]]
            [next.jdbc :as jdbc]
            [schema.core :as s]
            [taoensso.timbre :refer [info debug error]]))

(defn transform_ml [hashMap]
  "Builds Map with keys as keywords and values from HashMap (sql-hstore)"
  (if (nil? hashMap)
    nil
    (keywordize-keys (zipmap (.keySet hashMap) (.values hashMap)))))

(def schema_ml_list
  {(s/optional-key :de) (s/maybe s/Str)
   (s/optional-key :en) (s/maybe s/Str)})

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
    (if-let [result-db (dbh/query-eq-find-one db_table db_col_name search tx)]
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
    (if-let [result-db (dbh/query-eq-find-one db_table db_col_name search tx)]
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
  (if-let [result-db (dbh/query-eq-find-one db_table db_col_name search db_col_name2 search2 (:tx request))]
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
        res (dbh/query-eq-find-one db_table db_col_name search db_col_name2 search2 tx)]

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

; begin swagger docu summary helpers

(def s_cnv_acc "Convenience access.")

;; TODO: DEV-HELPER (remove this section afterwards)
;; additional dev/debug-tags for roles that has to be confirmed by @drtom
(def SHOW_MR_INFO_DEV_MODE false)
(defn doc [filepath] (if SHOW_MR_INFO_DEV_MODE "md/api-description-dev.md" filepath))
(defn debug-info [text] (if SHOW_MR_INFO_DEV_MODE text ""))
(defn ?sum_usr_pub? [text] (if SHOW_MR_INFO_DEV_MODE (apply str "?PUBLIC/USER Context? " text) text))
(defn ?sum_pub? [text] (if SHOW_MR_INFO_DEV_MODE (apply str "?PUBLIC Context? " text) text))
(defn ?sum_usr? [text] (if SHOW_MR_INFO_DEV_MODE (apply str "?USER Context? " text) text))

;; Flag to append
(defn ?token? [text] (if SHOW_MR_INFO_DEV_MODE (apply str text " [mr/ IST / token-auth]")) text)
(defn ?session? [text] (if SHOW_MR_INFO_DEV_MODE (apply str text " [mr / IST / session-auth]") text))
(defn ?no-auth? [text] (if SHOW_MR_INFO_DEV_MODE (apply str text " [mr / IST / no-auth]") text))
(defn session-req [text] (apply str text " [session required]"))

(defn create-example-response
  ([schema value]
   {:content {"application/json" {:schema schema
                                  :examples {:error {:value value}}}}})
  ([description schema value]
   {:description description
    :content {"application/json" {:schema schema
                                  :examples {:error {:value value}}}}}))

(defn create-error-message-response
  ([message]
   (create-example-response {:message s/Str}
                            {:message message}))
  ([description message]
   (create-example-response description {:message s/Str}
                            {:message message})))

(defn create-error-message-response-spec
  ([message]
   (create-example-response {:message string?}
                            {:message message}))
  ([description message]
   (create-example-response description {:message string?}
                            {:message message})))

(defn create-examples-response
  ([schema examples]
   {:content {"application/json" {:schema schema
                                  :examples (vec
                                             (map (fn [{:keys [summary value]}]
                                                    {:summary summary
                                                     :value value})
                                                  examples))}}})
  ([description schema examples]
   {:description description
    :content {"application/json" {:schema schema
                                  :examples (vec
                                             (map (fn [{:keys [summary value]}]
                                                    {:summary summary
                                                     :value value})
                                                  examples))}}}))

(defn sum_todo [text] (apply str "TODO: " text))
(defn sum_pub [text] (apply str "PUBLIC Context: " text))
(defn sum_usr [text] (apply str "USER Context: " text))
(defn sum_usr_pub [text] (apply str "PUBLIC/USER Context: " text))
(defn sum_adm [text] (apply str "ADMIN Context: " text))
(defn sum_auth [text] (apply str "Authorized Context: " text))

(defn sum_cnv [text] (apply str text " " s_cnv_acc))

;; TODO: no usage
(defn sum_cnv_adm [text] (sum_adm (sum_cnv text)))

(defn sum_adm_todo [text] (sum_todo (sum_adm text)))
; end swagger docu summary helpers

(defn parsed_response_exception [ex]
  (let [msg (ex-message ex)]
    (error msg)
    (debug ex)
    (response_failed msg 500)))

(defn transform_ml_map [data]
  (cond-> data
    (:labels data) (assoc :labels (transform_ml (:labels data)))
    (:descriptions data) (assoc :descriptions (transform_ml (:descriptions data)))
    (:contents data) (assoc :contents (transform_ml (:contents data)))))

;(debug/debug-ns *ns*)
