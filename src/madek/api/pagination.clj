(ns madek.api.pagination
  (:require
    [clojure.tools.logging :as logging]
    [clojure.walk :refer [keywordize-keys]]
    [compojure.core :as cpj]
    [logbug.debug :as debug]
    [madek.api.utils.rdbms :as rdbms]
    [madek.api.utils.sql :as sql]
    ))

(def LIMIT 100)

(defn page-number [params]
  (let [page (-> params keywordize-keys :page)]
    (if (not= nil page)
      (let [pagen (Integer/parseInt page)]
        (or pagen 0))
      0)
    )
  )

(defn compute-offset [params]
  (* LIMIT (page-number params)))

(defn add-offset-for-honeysql [query params]
  (let [off (compute-offset params)]
    (-> query
        (sql/offset off)
        (sql/limit LIMIT))))

(defn next-page-query-query-params [query-params]
  (let [query-params (keywordize-keys query-params)
        i-page (page-number query-params)]
    (assoc query-params
           :page (+ i-page 1))))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
