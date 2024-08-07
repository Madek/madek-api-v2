(ns madek.api.utils.soft-delete
  (:require [honey.sql.helpers :as sql]))

(defn- non-soft-delete-raw
  ([]
   [:raw (str "(deleted_at is null or now() < deleted_at)")])
  ([table-name]
   [:raw (str "(" table-name ".deleted_at is null or now() < " table-name ".deleted_at)")]))

(defn- soft-delete-raw [table-name]
  [:raw "(" table-name ".deleted_at is not null and now() >= " table-name ".deleted_at)"])

(defn non-soft-deleted
  ([table-name]
   (sql/where (non-soft-delete-raw table-name)))
  ([where-query table-name]
   (sql/where where-query (non-soft-delete-raw table-name))))

(defn ->non-soft-deleted
  ([query]
   (-> query (sql/where (non-soft-delete-raw))))
  ([query table-name]
   (-> query (sql/where (non-soft-delete-raw table-name)))))

(defn soft-deleted
  ([table-name]
   (sql/where (soft-delete-raw table-name)))
  ([where-query table-name]
   (sql/where where-query (soft-delete-raw table-name))))
