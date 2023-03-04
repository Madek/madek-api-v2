(ns util-create-data-test
  (:require 
   [clj-http.client :as client]
   [clojure.test :refer [deftest is run-tests run-all-tests]]
   [api-test-utils :as tu]
  
   [api-test-data :as td]))


(deftest dev-create-user (tu/init-db td/dburldev) (tu/del-test-user2) (tu/del-test-person2) (tu/init-test-person2) (tu/init-test-user2))

(deftest dev-create-admin-user (tu/init-db td/dburldev) (tu/del-test-admin) (tu/del-test-user) (tu/del-test-person) (tu/init-test-person) (tu/init-test-user) (tu/init-test-admin))

(deftest test-create-user (tu/init-db td/dburltest) (tu/del-test-user2) (tu/del-test-person2) (tu/init-test-person2) (tu/init-test-user2))

(deftest test-create-admin-user (tu/init-db td/dburltest) (tu/del-test-admin) (tu/del-test-user) (tu/del-test-person) (tu/init-test-person) (tu/init-test-user) (tu/init-test-admin))

(deftest test-true
  (is true? true))

(run-all-tests)