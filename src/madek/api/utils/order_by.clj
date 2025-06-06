(ns madek.api.utils.order-by
  (:require
   ;[honeysql.core         :as sql]
   ;[honeysql.format       :refer [format]]

   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]

   [next.jdbc             :as jdbc]))



;(ns your-app.db
;  (:require
;   [honeysql.core   :as sql]
;   [honeysql.format :refer [format]]
;   [next.jdbc       :as jdbc]))

;; ----------------------------------------------------------------------------
;; 1) Tables with “id” but NOT “created_at”
;; ----------------------------------------------------------------------------
(defn get-tables-with-id
  [ds]
  (let [query-map (->
                   ;(sql/select :table_name)
                   ;   (sql/from :information_schema.columns)
                   ;   (sql/where [:= :table_schema "public"])
                   ;   (sql/group-by :table_name)
                   ;   (sql/having
                   ;     [:and
                   ;      [[:>
                   ;        [:sum [:cast [:= :column_name "id"] :int]]
                   ;        0]
                   ;       [:=
                   ;        [:sum [:cast [:= :column_name "created_at"] :int]]
                   ;        0]]])


                   (sql/select :* )
                   (sql/from :users)

                      sql-format
                      )

        p (println ">o> abc.q" query-map)
        p (println ">o> abc.ds" ds)

        ;[sql-str & params] (sql-format query-map)
        ;rows (jdbc/execute! ds (into [sql-str] params))]
        ;rows (jdbc/execute! ds (sql-format query-map))]
        rows (jdbc/execute-one! ds query-map)


        p (println ">o> abc.rows" rows)
        ]
    (mapv :table_name rows)))

;; ----------------------------------------------------------------------------
;; 2) Tables with BOTH “id” AND “created_at”
;; ----------------------------------------------------------------------------
(defn get-tables-with-id-and-created-at
  [ds]
  (let [query-map (-> (sql/select :table_name)
                      (sql/from :information_schema.columns)
                      (sql/where [:= :table_schema "public"])
                      (sql/group-by :table_name)
                      (sql/having
                        [:and
                         [[:>
                           [:sum [:cast [:= :column_name "id"] :int]]
                           0]
                          [[:>
                            [:sum [:cast [:= :column_name "created_at"] :int]]
                            0]]]]))
        ;[sql-str & params] (sql-format query-map)
        ;rows (jdbc/execute! ds (into [sql-str] params))]
        rows (jdbc/execute! ds (sql-format query-map))]
    (mapv :table_name rows)))

;; ----------------------------------------------------------------------------
;; 3) Tables with “created_at” but NOT “id”
;; ----------------------------------------------------------------------------
(defn get-tables-with-created-at-only
  [ds]
  (let [query-map (-> (sql/select :table_name)
                      (sql/from :information_schema.columns)
                      (sql/where [:= :table_schema "public"])
                      (sql/group-by :table_name)
                      (sql/having
                        [:and
                         [:=
                          [:sum [:cast [:= :column_name "id"] :int]]
                          0]
                         [[:>
                           [:sum [:cast [:= :column_name "created_at"] :int]]
                           0]]]))
        ;[sql-str & params] (sql-format query-map)
        ;rows (jdbc/execute! ds (into [sql-str] params))]
        rows (jdbc/execute! ds (sql-format query-map))]
    (mapv :table_name rows)))

;; ----------------------------------------------------------------------------
;; 4) Tables with NEITHER “id” NOR “created_at”
;; ----------------------------------------------------------------------------
(defn get-tables-with-neither
  [ds]
  (let [;; Subquery “c” computes has_id & has_created_at per table
        subq (-> (sql/select  :table_name
                   [[:max [:cast [:= :column_name "id"] :int]] :has_id]
                   [[:max [:cast [:= :column_name "created_at"] :int]] :has_created_at])
                 (sql/from   :information_schema.columns)
                 (sql/where  [:= :table_schema "public"])
                 (sql/group-by :table_name))
        ;; Main query: LEFT JOIN information_schema.tables AS t to subq AS c
        query-map (-> (sql/select [[:t.table_name :table_name]])
                      (sql/from   [[:information_schema.tables :t]])
                      (sql/left-join
                        [subq :c] [:= :t.table_name :c.table_name])
                      (sql/where
                        [:and
                         [:= :t.table_schema    "public"]
                         [:= :t.table_type      "BASE TABLE"]
                         [:= [:coalesce :c.has_id         0] 0]
                         [:= [:coalesce :c.has_created_at 0] 0]]))
        ;[sql-str & params] (sql-format query-map)
        ;rows (jdbc/execute! ds (into [sql-str] params))]
        rows (jdbc/execute! ds (sql-format query-map))]
    (mapv :table_name rows)))