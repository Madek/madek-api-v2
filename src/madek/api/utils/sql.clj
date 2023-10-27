(ns madek.api.utils.sql
  (:import [java.sql PreparedStatement]
           [org.postgresql.util HStoreConverter])
  (:refer-clojure :exclude [format])
  (:require
    [clojure.tools.logging :as logging]
    [honeysql.format :as format]
    [honeysql.helpers :as helpers :refer [defhelper]]
    [honeysql.types :as types]
    [honeysql.util :as util :refer [defalias]]
    [cheshire.core :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.walk :refer [keywordize-keys]]
    ))

(defmethod format/fn-handler "~*" [_ field value]
  (str (format/to-sql field) " ~* " (format/to-sql value)))

(defn dedup-join [honeymap]
  (assoc honeymap :join
         (reduce #(let [[k v] %2] (conj %1 k v)) []
                 (clojure.core/distinct (partition 2 (:join honeymap))))))

(defn format
  "Calls honeysql.format/format with removed join duplications in sql-map."
  [sql-map & params-or-opts]
  (apply format/format [(dedup-join sql-map) params-or-opts]))


(defalias call types/call)
(defalias param types/param)
(defalias raw types/raw)

(defalias format-predicate format/format-predicate)
(defalias quote-identifier format/quote-identifier)

(defalias columns helpers/columns)
(defalias delete-from helpers/delete-from)
(defalias from helpers/from)

(defalias group helpers/group)
(defalias insert-into helpers/insert-into)
(defalias join helpers/join)
(defalias limit helpers/limit)
(defalias merge-join helpers/merge-join)
(defalias merge-left-join helpers/merge-left-join)
(defalias merge-select helpers/merge-select)
(defalias merge-where helpers/merge-where)
(defalias modifiers helpers/modifiers)
(defalias offset helpers/offset)
(defalias order-by helpers/order-by)
(defalias merge-order-by helpers/merge-order-by)
(defhelper returning [m fields]
  (assoc m :returning (helpers/collify fields)))
(defalias select helpers/select)
(defhelper using [m tables]
  (assoc m :using (helpers/collify tables)))
(defalias values helpers/values)
(defalias where helpers/where)

;#############################################################################


(def ->json json/generate-string)
(def <-json #(json/parse-string % true))

(def ->hstore #(HStoreConverter/toString (update-keys % name)))
;(def <-hstore #(update-keys (HStoreConverter/fromString %) keyword))
(def <-hstore (fn [string_data]
                (let [hashMap (update-keys (HStoreConverter/fromString string_data) keyword)
                      pmap (keywordize-keys (zipmap (.keySet hashMap) (.values hashMap)))]
                  (logging/info "<-hstore: hashMap:\n" hashMap "\npmap:\n" pmap)
                  pmap)))

(defn ->pgobject
  [x]
  (let [pgtype (or (:pgtype (meta x)) "hstore")] ;"jsonb")]
    #_(logging/info "->pgobject: \nmeta type\n " (:pgtype (meta x)) ":" pgtype)
    (doto (org.postgresql.util.PGobject.)
      (.setType pgtype)
      (.setValue (condp contains? pgtype
                   #{"json" "jsonb"} (->json x)
                   #{"hstore"} (->hstore x)
                   (throw (ex-info "unknown postgresql type" {:type pgtype})))))))

(defn <-pgobject
  [^org.postgresql.util.PGobject v]
  (let [type  (.getType v)
        value (.getValue v)]
    #_(logging/info "<-pgobject: \nmeta type\n " type " value " value)
    (condp contains? type
      #{"jsonb" "json"} (when value
                          (with-meta (<-json value) {:pgtype type}))
      #{"hstore"} (when value
                    (with-meta (<-hstore value) {:pgtype type}))
      value)))

(extend-protocol jdbc/ISQLParameter
  clojure.lang.IPersistentMap
  (set-parameter [m ^PreparedStatement s i]
    (.setObject s i (->pgobject m)))

  clojure.lang.IPersistentVector
  (set-parameter [v ^java.sql.PreparedStatement stmt ^long i]
                  (let [conn (.getConnection stmt)
                        meta (.getParameterMetaData stmt)
                        type-name (.getParameterTypeName meta i)]
                    (if-let [elem-type (when (= (first type-name) \_) (apply str (rest type-name)))]
                      (.setObject stmt i (.createArrayOf conn elem-type (to-array v)))
                      (.setObject stmt i (->pgobject v))))))
  ;(set-parameter [v ^PreparedStatement s i]
  ;  (.setObject s i (->pgobject v))))


(extend-protocol jdbc/IResultSetReadColumn
  org.postgresql.util.PGobject
  (result-set-read-column [^org.postgresql.util.PGobject v _1 _2]
    (<-pgobject v))
  ;(read-column-by-label [^org.postgresql.util.PGobject v _]
  ;  (<-pgobject v))
  ;(read-column-by-index [^org.postgresql.util.PGobject v _2 _3]
  ;  (<-pgobject v))
  )


;#### debug ###################################################################
;(debug/debug-ns *ns*)
