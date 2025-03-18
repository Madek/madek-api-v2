(ns madek.api.resources.shared.db_helper
  (:require [clojure.string]
            [honey.sql :refer [format] :rename {format sql-format}]
            [honey.sql.helpers :as sql]
            [java-time.api :as jt]
            [logbug.catcher :as catcher]
            [madek.api.constants :as mc]
            [madek.api.resources.people.common :as people-common]
            [madek.api.resources.users.columns :as users-columns]
            [madek.api.utils.helper :refer [to-uuid]]
            [madek.api.utils.soft-delete :refer [->non-soft-deleted]]
            [next.jdbc :as jdbc]
            [taoensso.timbre :refer [error info spy]]))

; begin db-helpers
; TODO move to sql file
; TODO sql injection protection
(defn build-query-base [table-key col-keys]
  (-> (apply sql/select col-keys)
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

(defn build-query-param-like
  ([query query-params param]
   (build-query-param-like query query-params param param))
  ([query query-params param db-param]
   (let [pval (-> query-params param mc/presence)
         qval (str "%" pval "%")]
     (if (nil? pval)
       query
       (-> query (sql/where [:like db-param qval]))))))

(defn table->col-keys
  ([table] (table->col-keys table nil))
  ([table alias]
   (let [all :*
         col-keys (case table
                    :people (conj people-common/people-select-keys
                                  :people.searchable)
                    :users users-columns/user-select-keys
                    [(if alias
                       (->> all name
                            (str (name alias) ".")
                            keyword)
                       all)])]
     (if alias
       (map #(-> %
                 name
                 (clojure.string/replace (re-pattern (name table))
                                         alias)
                 keyword)
            col-keys)
       col-keys))))

(defn sql-query-find-eq
  ([table-name col-name row-data]
   (let [query (if (= col-name :media_entry_id)
                 (-> (build-query-base [table-name :vtable]
                                       (table->col-keys table-name :vtable))
                     (sql/join [:media_entries :me] [:= :vtable.media_entry_id :me.id])
                     (sql/where [:= :me.id (to-uuid row-data col-name table-name)])
                     (->non-soft-deleted "me")
                     sql-format)
                 (-> (build-query-base table-name (table->col-keys table-name))
                     (sql/where [:= col-name (to-uuid row-data col-name table-name)])
                     sql-format))]
     query))

  ([table-name col-name row-data col-name2 row-data2]
   (let [query (-> (build-query-base table-name (table->col-keys table-name))
                   (sql/where [:= col-name (to-uuid row-data col-name)])
                   (sql/where [:= col-name2 (to-uuid row-data2 col-name2)])
                   (sql-format :inline true)
                   spy)]
     query)))

(defn sql-update-clause
  "Generates an sql update clause"
  ([col-name row-data]
   [(str col-name " = ?") row-data])
  ([col-name row-data col-name2 row-data2]
   [(str col-name " = ? AND " col-name2 " = ? ") row-data row-data2])
  ([col-name row-data col-name2 row-data2 col-name3 row-data3]
   [(str col-name " = ? AND " col-name2 " = ? AND " col-name3 " = ? ") row-data row-data2 row-data3]))

(defn sql-update-fnc-clause
  "Generates an sql update clause"
  ([query col-name row-data col-name2 row-data2 col-name3 row-data3]
   (-> query
       (sql/where [:= (keyword col-name) row-data] [:= (keyword col-name2) row-data2] [:= (keyword col-name3) row-data3]))))

(defn hsql-upd-clause-format
  "Transforms honey sql to sql update clause"
  [sql-cls]
  (update-in sql-cls [0] #(clojure.string/replace % "WHERE" "")))

(defn query-find-all
  [table-key col-keys tx]
  (let [selected-cols (or (seq col-keys) (table->col-keys table-key))
        db-query (-> (build-query-base table-key selected-cols)
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

;(debug/debug-ns *ns*)
