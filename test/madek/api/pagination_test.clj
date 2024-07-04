(ns madek.api.pagination-test
  (:require
   [clojure.test :refer :all]
   [madek.api.pagination :refer [DEFAULT_LIMIT compute-offset page-count page-number sql-offset-and-limit]]))

(deftest sql-offset-and-limit-returns-zero-based-result
  (with-redefs [madek.api.pagination/ZERO_BASED_PAGINATION true]
    (let [query {}]
      (is (= {:limit DEFAULT_LIMIT :offset 0} (sql-offset-and-limit query {:page -5 :size -100})))

      (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {})))
      (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {:page 0 :size 100})))
      (is (= {:limit 100 :offset 100} (sql-offset-and-limit query {:page 1 :size 100})))
      (is (= {:limit 100 :offset 200} (sql-offset-and-limit query {:page 2 :size 100})))
      (is (= {:limit 100 :offset 300} (sql-offset-and-limit query {:page 3 :size 100}))))))

(deftest sql-offset-and-limit-returns-one-based-result
  (with-redefs [madek.api.pagination/ZERO_BASED_PAGINATION false]
    (testing "sql-offset-and-limit function, most relevant test cases for one-based-pagination"
      (let [query {}]
        (is (= {:limit DEFAULT_LIMIT :offset 0} (sql-offset-and-limit query {:page -5 :size -100})))

        (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {})))
        (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {:page 0 :size 100})))
        (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {:page 1 :size 100})))
        (is (= {:limit 100 :offset 100} (sql-offset-and-limit query {:page 2 :size 100})))
        (is (= {:limit 100 :offset 200} (sql-offset-and-limit query {:page 3 :size 100})))))))

(deftest page-number-returns-correct-value-test
  (testing "page-number function"
    (is (= 0 (page-number {:page 0})))
    (is (= 1 (page-number {:page 1})))
    (is (= 2 (page-number {:page 2})))
    (is (= 5 (page-number {:page 5})))
    (is (= 0 (page-number {:page -5})))))

(deftest page-count-returns-correct-value
  (testing "page-count function"
    (is (= DEFAULT_LIMIT (page-count {:count 0})))
    (is (= 1 (page-count {:count 1})))
    (is (= 5 (page-count {:count 5})))
    (is (= DEFAULT_LIMIT (page-count {:count -5})))
    (is (= DEFAULT_LIMIT (page-count {})))))

(deftest compute-offset-returns-correct-value
  (testing "compute-offset function"
    (is (= 0 (compute-offset {:page 0 :count 0})))
    (is (= 500 (compute-offset {:page 5 :count 100})))
    (is (= 0 (compute-offset {:page -5 :count -100})))))

(deftest sql-offset-and-limit-returns-correct-value
  (testing "sql-offset-and-limit function, handle page/count correctly"

    (with-redefs [madek.api.pagination/ZERO_BASED_PAGINATION true]

      (let [query {}]
        (is (= {:limit DEFAULT_LIMIT :offset 0} (sql-offset-and-limit query {:page 0 :count 0})))
        (is (= {:limit 100 :offset 500} (sql-offset-and-limit query {:page 5 :count 100})))
        (is (= {:limit DEFAULT_LIMIT :offset 0} (sql-offset-and-limit query {:page -5 :count -100})))
        (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {})))
        (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {:page 0 :count 100})))
        (is (= {:limit 100 :offset 100} (sql-offset-and-limit query {:page 1 :count 100})))
        (is (= {:limit 100 :offset 200} (sql-offset-and-limit query {:page 2 :count 100})))
        (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {:page 0})))
        (is (= {:limit 100 :offset 100} (sql-offset-and-limit query {:page 1})))
        (is (= {:limit 100 :offset 200} (sql-offset-and-limit query {:page 2})))
        (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {:count 100})))
        (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {:count 100})))))))

(deftest sql-offset-and-limit-returns-correct-value-for-size
  (testing "sql-offset-and-limit function, test renaming-feature"

    (with-redefs [madek.api.pagination/ZERO_BASED_PAGINATION true]

      (let [query {}]
        (is (= {:limit DEFAULT_LIMIT :offset 0} (sql-offset-and-limit query {:page 0 :size 0})))
        (is (= {:limit 100 :offset 500} (sql-offset-and-limit query {:page 5 :size 100})))
        (is (= {:limit DEFAULT_LIMIT :offset 0} (sql-offset-and-limit query {:page -5 :size -100})))
        (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {})))
        (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {:page 0 :size 100})))
        (is (= {:limit 100 :offset 100} (sql-offset-and-limit query {:page 1 :size 100})))
        (is (= {:limit 100 :offset 200} (sql-offset-and-limit query {:page 2 :size 100})))
        (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {:page 0})))
        (is (= {:limit 100 :offset 100} (sql-offset-and-limit query {:page 1})))
        (is (= {:limit 100 :offset 200} (sql-offset-and-limit query {:page 2})))
        (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {:size 100})))
        (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {:size 100})))))))

(deftest sql-offset-and-limit-returns-correct-value
  (testing "sql-offset-and-limit function, handle page/count correctly"
    (with-redefs [madek.api.pagination/ZERO_BASED_PAGINATION false]
      (let [query {}]
        (is (= {:limit DEFAULT_LIMIT :offset 0} (sql-offset-and-limit query {:page 0 :count 0})))
        (is (= {:limit 100 :offset 400} (sql-offset-and-limit query {:page 5 :count 100})))
        (is (= {:limit DEFAULT_LIMIT :offset 0} (sql-offset-and-limit query {:page -5 :count -100})))
        (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {})))
        (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {:page 0 :count 100})))
        (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {:page 1 :count 100})))
        (is (= {:limit 100 :offset 100} (sql-offset-and-limit query {:page 2 :count 100})))
        (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {:page 0})))
        (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {:page 1})))
        (is (= {:limit 100 :offset 100} (sql-offset-and-limit query {:page 2})))
        (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {:count 100})))
        (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {:count 100})))))))

(deftest sql-offset-and-limit-returns-correct-value-for-size
  (testing "sql-offset-and-limit function, test renaming-feature"
    (with-redefs [madek.api.pagination/ZERO_BASED_PAGINATION false]
      (let [query {}]
        (is (= {:limit DEFAULT_LIMIT :offset 0} (sql-offset-and-limit query {:page 0 :size 0})))
        (is (= {:limit 100 :offset 400} (sql-offset-and-limit query {:page 5 :size 100})))
        (is (= {:limit DEFAULT_LIMIT :offset 0} (sql-offset-and-limit query {:page -5 :size -100})))
        (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {})))
        (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {:page 0 :size 100})))
        (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {:page 1 :size 100})))
        (is (= {:limit 100 :offset 100} (sql-offset-and-limit query {:page 2 :size 100})))
        (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {:page 0})))
        (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {:page 1})))
        (is (= {:limit 100 :offset 100} (sql-offset-and-limit query {:page 2})))
        (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {:size 100})))
        (is (= {:limit 100 :offset 0} (sql-offset-and-limit query {:size 100})))))))

(run-tests)
