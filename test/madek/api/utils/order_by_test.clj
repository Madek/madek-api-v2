(ns madek.api.utils.order-by-test
  (:require
   [clojure.test :refer :all]
   [honey.sql.helpers      :as sql]
   [honey.sql               :refer [format] :rename {format sql-format}]
   [next.jdbc               :as jdbc]
   [madek.api.db.core       :refer [get-ds]]
   [madek.api.utils.rdbms  :as rdbms]       ;; for initialize
   ;; The functions that return table‐name vectors:
   [madek.api.utils.order-by
    :refer [ get-tables-with-id
            get-tables-with-id-and-created-at
            get-tables-with-created-at-only
            get-tables-with-neither ]]))


;;------------------------------------------------------------------------------
;; Instead of hard‐coding `db-spec` in each `jdbc/execute!` call, we:
;; 1) Initialize once (fixture) with `init-db`, passing in the same map you would
;;    have used for a `jdbc/get-datasource` call.
;; 2) Call `(get-ds)` inside tests to retrieve the ready `ds`.
;;------------------------------------------------------------------------------

(def db-spec
  {:dbtype   "postgresql"
   :dbname   "madek_test"
   :user     "madek_sql"
   :port     5415
   :password "madek_sql"})

(defn init-db []
  ;; Make sure rdbms/initialize is called with the same spec that get-ds expects.
  ;; After this, (get-ds) should return a valid datasource for all tests.
  (rdbms/initialize db-spec)
  (get-ds))

(init-db)


;(use-fixtures
;  :once
;  (fn [tests]
;    ;; Initialize the DB once before any tests run
;    (init-db)
;    (tests)))


;;
;; Test 1: For every table that has an “id” column (including those
;;         that also have created_at), run a simple SELECT … ORDER BY id
;;
;(deftest order-by-test-id-works
;(defn order-by-test-id-works []
(deftest order-by-test-id-works []
  (let [
        ;ds (get-ds)
        ds (rdbms/get-ds)

        p (println ">o> abc.ds" ds)

        tables-with-id-only       (get-tables-with-id ds)
        ;tables-with-both          (get-tables-with-id-and-created-at ds)
        ;all-id-tables             (concat tables-with-id-only tables-with-both)
        ]
    ;(doseq [table-name all-id-tables]
    ;  (let [query-map      (-> (sql/select :*)
    ;                           (sql/from (keyword table-name))
    ;                           (sql/order-by :id))
    ;        ;[sql-str & params] (sql-format query-map)
    ;        ;result         (jdbc/execute! ds (into [sql-str] params))]
    ;        result         (jdbc/execute! ds (sql-format query-map))]
    ;    (is (vector? result)
    ;      (str "Expected a vector of rows when ORDER BY id on “"
    ;           table-name
    ;        "”, but got: " (pr-str result)))))

    ))


;;
;; Test 2: For every table that has a “created_at” column (whether or not
;;         it also has “id”), run SELECT * … ORDER BY created_at
;;
;(deftest order-by-created-at-works
;  (let [ds                        (get-ds)
;        tables-with-created-only   (get-tables-with-created-at-only ds)
;        tables-with-both           (get-tables-with-id-and-created-at ds)
;        all-created-at-tables      (concat tables-with-created-only tables-with-both)]
;    (doseq [table-name all-created-at-tables]
;      (let [query-map      (-> (sql/select :*)
;                               (sql/from (keyword table-name))
;                               (sql/order-by :created_at))
;            [sql-str & params] (sql-format query-map)
;            result         (jdbc/execute! ds (into [sql-str] params))]
;        (is (vector? result)
;          (str "Expected a vector of rows when ORDER BY created_at on “"
;               table-name
;            "”, but got: " (pr-str result)))))))
;
;
;;;
;;; Test 3: For tables that have BOTH “id” AND “created_at”, double‐check
;;;         both ORDER BY id and ORDER BY created_at run without error.
;;;
;(deftest order-by-id-and-created-at-works
;  (let [ds               (get-ds)
;        tables-with-both (get-tables-with-id-and-created-at ds)]
;    (doseq [table-name tables-with-both]
;      (let [q-id-map         (-> (sql/select :*)
;                                 (sql/from (keyword table-name))
;                                 (sql/order-by :id))
;            q-ca-map         (-> (sql/select :*)
;                                 (sql/from (keyword table-name))
;                                 (sql/order-by :created_at))
;            [sql-id-str & id-params] (sql-format q-id-map)
;            [sql-ca-str & ca-params] (sql-format q-ca-map)
;            result-id        (jdbc/execute! ds (into [sql-id-str] id-params))
;            result-ca        (jdbc/execute! ds (into [sql-ca-str] ca-params))]
;        (is (vector? result-id)
;          (str "ORDER BY id failed on “" table-name "”: " (pr-str result-id)))
;        (is (vector? result-ca)
;          (str "ORDER BY created_at failed on “" table-name "”: " (pr-str result-ca)))))))
;
;
;;;
;;; Test 4: For tables that have NEITHER “id” NOR “created_at”, attempting
;;;         ORDER BY either column should throw an SQL exception.
;;;
;(deftest ordering-on-nonexistent-column-throws
;  (let [ds                  (get-ds)
;        tables-with-neither  (get-tables-with-neither ds)]
;    (doseq [table-name tables-with-neither]
;      ;; ORDER BY id should throw
;      (let [q-id-map       (-> (sql/select :*)
;                               (sql/from (keyword table-name))
;                               (sql/order-by :id))
;            [sql-id-str & id-params] (sql-format q-id-map)]
;        (is (thrown? Exception
;              (jdbc/execute! ds (into [sql-id-str] id-params)))
;          (str "Expected ORDER BY id to throw on “" table-name "”, but it did not.")))
;      ;; ORDER BY created_at should also throw
;      (let [q-ca-map       (-> (sql/select :*)
;                               (sql/from (keyword table-name))
;                               (sql/order-by :created_at))
;            [sql-ca-str & ca-params] (sql-format q-ca-map)]
;        (is (thrown? Exception
;              (jdbc/execute! ds (into [sql-ca-str] ca-params)))
;          (str "Expected ORDER BY created_at to throw on “" table-name "”, but it did not."))))))

