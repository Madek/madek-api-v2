(ns madek.api.utils.order-by
  (:require
   ;[honeysql.core         :as sql]
   ;[honeysql.format       :refer [format]]

   [honey.sql :refer [format] :rename {format sql-format}]

   [honey.sql.helpers :as sql]
   [madek.api.db.core :refer [builder-fn-options-default]]
   [next.jdbc :as jdbc]))



;(ns your-app.db
;  (:require
;   [honeysql.core   :as sql]
;   [honeysql.format :refer [format]]
;   [next.jdbc       :as jdbc]))


(defn pr [str fnc]
  ;(println ">oo> HELPER / " str fnc)(println ">oo> HELPER / " str fnc)
  (println ">oo> " str fnc)
  fnc
  )







;; ----------------------------------------------------------------------------
;; 1) Tables with “id” but NOT “created_at”
;; ----------------------------------------------------------------------------
(defn get-tables-with-id-without-created-at
  [ds]
  (let [
        query-map ["SELECT table_name
    FROM information_schema.columns
    WHERE table_schema = ?
    GROUP BY table_name
    HAVING COUNT(CASE WHEN column_name = 'id' THEN 1 END) > 0
      AND COUNT(CASE WHEN column_name = 'created_at' THEN 1 END) = 0"
                   "public"]

        p (println ">o> abc1?" query-map)

        ;rows (jdbc/execute! ds (sql-format query-map))]
        ;rows (jdbc/execute! ds query-map {:builder-fn rs/as-unqualified-lower-maps})
        rows (jdbc/execute! ds query-map builder-fn-options-default)
        p (println ">o> abc2?" rows)
        ]
    (pr ">>1 get-tables-with-id => " (mapv :table_name rows))))

;; ----------------------------------------------------------------------------
;; 2) Tables with BOTH “id” AND “created_at”
;; ----------------------------------------------------------------------------
;(defn get-tables-with-id-and-created-at
;  [ds]
;  (let [query-map (-> (sql/select :table_name)
;                      (sql/from :information_schema.columns)
;                      (sql/where [:= :table_schema "public"])
;                      (sql/group-by :table_name)
;                      (sql/having
;                        [:and
;                         [[:>
;                           [:sum [:cast [:= :column_name "id"] :int]]
;                           0]
;                          [[:>
;                            [:sum [:cast [:= :column_name "created_at"] :int]]
;                            0]]]]))
;
;
;
;        ;[sql-str & params] (sql-format query-map)
;        ;rows (jdbc/execute! ds (into [sql-str] params))]
;        rows (jdbc/execute! ds (sql-format query-map))]
;    (mapv :table_name rows)))
;
;(require '[clojure.java.jdbc :as jdbc])

(defn get-tables-with-id-and-created-at
  [ds]
  (let [sql
        "SELECT table_name
         FROM information_schema.columns
         WHERE table_schema = ?
         GROUP BY table_name
         HAVING COUNT(*) FILTER (WHERE column_name = 'id')      > 0
            AND COUNT(*) FILTER (WHERE column_name = 'created_at') > 0;"
        ;; Pass the SQL and params as a vector:
        rows (jdbc/execute! ds [sql "public"])]
    ;; rows will be something like
    ;; [{:table_name \"contexts\"} {:table_name \"delegations\"} …]
    (pr ">>2 get-tables-with-id-and-created-at => " (mapv :table_name rows))))


;; ----------------------------------------------------------------------------
;; 3) Tables with “created_at” but NOT “id”
;; ----------------------------------------------------------------------------
;(defn get-tables-with-created-at-only
;  [ds]
;  (let [query-map (-> (sql/select :table_name)
;                      (sql/from :information_schema.columns)
;                      (sql/where [:= :table_schema "public"])
;                      (sql/group-by :table_name)
;                      (sql/having
;                        [:and
;                         [:=
;                          [:sum [:cast [:= :column_name "id"] :int]]
;                          0]
;                         [[:>
;                           [:sum [:cast [:= :column_name "created_at"] :int]]
;                           0]]]))
;        ;[sql-str & params] (sql-format query-map)
;        ;rows (jdbc/execute! ds (into [sql-str] params))]
;        rows (jdbc/execute! ds (sql-format query-map))]
;    (mapv :table_name rows)))
;
;(require '[clojure.java.jdbc :as jdbc])

(defn get-tables-with-created-at-only
  [ds]
  (let [sql
        "SELECT table_name
         FROM information_schema.columns
         WHERE table_schema = ?
         GROUP BY table_name
         HAVING COUNT(*) FILTER (WHERE column_name = 'id')          = 0
            AND COUNT(*) FILTER (WHERE column_name = 'created_at') >  0;"
        rows (jdbc/execute! ds [sql "public"])]
    ;; rows => [{:table_name "meta_data"} {:table_name "meta_data_people"} …]
    ;(mapv :table_name rows)))
    (pr ">>3 get-tables-with-created-at-only => " (mapv :table_name rows))))


;; ----------------------------------------------------------------------------
;; 4) Tables with NEITHER “id” NOR “created_at”
;; ----------------------------------------------------------------------------
;(defn get-tables-with-neither
;  [ds]
;  (let [;; Subquery “c” computes has_id & has_created_at per table
;        subq (-> (sql/select  :table_name
;                   [[:max [:cast [:= :column_name "id"] :int]] :has_id]
;                   [[:max [:cast [:= :column_name "created_at"] :int]] :has_created_at])
;                 (sql/from   :information_schema.columns)
;                 (sql/where  [:= :table_schema "public"])
;                 (sql/group-by :table_name))
;        ;; Main query: LEFT JOIN information_schema.tables AS t to subq AS c
;        query-map (-> (sql/select [[:t.table_name :table_name]])
;                      (sql/from   [[:information_schema.tables :t]])
;                      (sql/left-join
;                        [subq :c] [:= :t.table_name :c.table_name])
;                      (sql/where
;                        [:and
;                         [:= :t.table_schema    "public"]
;                         [:= :t.table_type      "BASE TABLE"]
;                         [:= [:coalesce :c.has_id         0] 0]
;                         [:= [:coalesce :c.has_created_at 0] 0]]))
;        ;[sql-str & params] (sql-format query-map)
;        ;rows (jdbc/execute! ds (into [sql-str] params))]
;        rows (jdbc/execute! ds (sql-format query-map))]
;    (mapv :table_name rows)))


(defn get-tables-with-neither
  [ds]
  (let [sql
        "SELECT t.table_name
         FROM information_schema.tables AS t
         LEFT JOIN (
           SELECT
             table_name,
             MAX((column_name = 'id')::int)          AS has_id,
             MAX((column_name = 'created_at')::int) AS has_created_at
           FROM information_schema.columns
           WHERE table_schema = ?
           GROUP BY table_name
         ) AS c
         ON t.table_name = c.table_name
         WHERE t.table_schema    = ?
           AND t.table_type      = 'BASE TABLE'
           AND COALESCE(c.has_id, 0)          = 0
           AND COALESCE(c.has_created_at, 0)  = 0;"
        ;; we pass "public" twice: once for the subquery, once for the outer WHERE
        rows (jdbc/execute! ds [sql "public" "public"])]
    ;; rows => [{:table_name "meta_keys"} {:table_name "vocabularies"} …]
    ;(mapv :table_name rows)))
    (pr ">>4 get-tables-with-neither => " (mapv :table_name rows))))




;; At the top of your namespace:
(def ^:private tables-with-id-without-created-at (atom []))
(def ^:private tables-with-id-and-created-at (atom []))
(def ^:private tables-with-created-at-only (atom []))
(def ^:private tables-with-neither (atom []))

(defn init-order-config-fnc
  [ds]
  (println "\n\n>o> call-all-fnc? ds" ds)
  (reset! tables-with-id-without-created-at (get-tables-with-id-without-created-at ds))
  (reset! tables-with-id-and-created-at (get-tables-with-id-and-created-at ds))
  (reset! tables-with-created-at-only (get-tables-with-created-at-only ds))
  (reset! tables-with-neither (get-tables-with-neither ds))
  (println ">o> initialized globals:\n"
    {:id-only @tables-with-id-without-created-at
     :both @tables-with-id-and-created-at
     :created-at @tables-with-created-at-only
     :neither @tables-with-neither})
  ;; return something or nil as needed
  nil)

(def additional-order {
                       :admins [[:user_id :desc]]

                       })


(defn merge-order-maps [col orders]

  (if (nil? (col additional-order))
    orders
    (into (col additional-order) orders)
    )
  )

;; 1. Top-level atom to hold the map
(defonce ^:private table-order-config (atom {}))

;; 2. Init fn to populate it
(defn init-order-config-fnc
  [ds]
  (let [;; pull in your four lists
        id-only (get-tables-with-id-without-created-at ds)
        both (get-tables-with-id-and-created-at ds)
        created-only (get-tables-with-created-at-only ds)
        neither (get-tables-with-neither ds)


        additional-order {
                          :admins [[:user_id :desc]]
                          }

        ;; build sub-maps for each case
        ;m-id-only (into {} (for [t id-only] [(keyword t) [:id]]))
        m-id-only (into {} (for [t id-only] [(keyword t) (merge-order-maps (keyword t) [:id])]))

        ;m-both         (into {} (for [t both]         [(keyword t) [[:created_at :desc] :id]]))
        m-both (into {} (for [t both] [(keyword t) (merge-order-maps (keyword t) [[:created_at :desc] [:id :asc]])]))

        ;m-created-only (into {} (for [t created-only] [(keyword t) [[:created_at :desc]]]))
        m-created-only (into {} (for [t created-only] [(keyword t) (merge-order-maps (keyword t) [[:created_at :desc]])]))

        m-neither (into {} (for [t neither] [(keyword t) nil]))





        ;; merge into one map
        full-map (merge m-id-only
                   m-both
                   m-created-only
                   m-neither)]

    ;; reset the atom so readers see the new config
    (reset! table-order-config full-map)

    ;; debug print if you like
    (println "Initialized table-order-config:" @table-order-config)


    (println ">o> abc.roles" (get @table-order-config "roles"))

    (let [

          limit 1

          ;            col :roles
          ;_ (println ">o> abc.roles" (get @table-order-config col))
          ;_ (println ">o> abc.roles" (-> (sql/select :*)
          ;                               (sql/from col)
          ;                               (sql/order-by
          ;                                ;(get-in @table-order-config [col 0]))
          ;                                (get-in @table-order-config [col]))
          ;    sql-format))
          ;
          ;         _ (println ">o> abc.roles" (jdbc/execute! ds (-> (sql/select :*)
          ;                               (sql/from col)
          ;                               (sql/order-by
          ;                                ;(get-in @table-order-config [col 0]))
          ;                                (get-in @table-order-config [col]))
          ;    sql-format)))



          ;; ------------------------------

          col :admins
          _ (println ">o> abc.:admins1" (get @table-order-config col))

          ;_ (println ">o> abc.:admins" (-> (sql/select :*)
          ;                               (sql/from col)
          ;                                 (sql/limit limit)
          ;                                 (sql/order-by
          ;                                (get-in @table-order-config [col]))
          ;                                ;(get-in @table-order-config [col 0]))
          ;    sql-format))

          _ (println ">o> abc.:admins2" (-> (sql/select :*)
                                            (sql/from col)
                                            (sql/limit limit)
                                            (#(apply sql/order-by % (get-in @table-order-config [col])))
                                            sql-format))

          _ (println ">o> abc.:admins3" (jdbc/execute! ds (-> (sql/select :*)
                                                              (sql/from col)
                                                              (sql/limit limit)
                                                              (#(apply sql/order-by % (get-in @table-order-config [col])))
                                                              sql-format)))


          ;         ;; ------------------------------
          ;         col :full_texts
          ;_ (println ">o> abc.::full_texts" (get @table-order-config col))
          ;_ (println ">o> abc.::full_texts" (-> (sql/select :*)
          ;                               (sql/from col)
          ;                                      (sql/limit limit)
          ;                               (sql/order-by
          ;                                ;(get-in @table-order-config [col 0]))
          ;                                (get-in @table-order-config [col]))
          ;    sql-format))
          ;
          ;
          ;
          ;         order (get @table-order-config col)
          ;         _ (println ">o> abc.::full_texts" (jdbc/execute! ds (-> (cond-> (-> (sql/select :*)
          ;                                                                         (sql/from col))
          ;                                                               order (sql/order-by order))
          ;                                                                 (sql/limit limit)
          ;                                                                 sql-format)))

          ])

    ))