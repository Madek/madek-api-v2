(ns madek.api.pagination
  (:require
    [clojure.tools.logging :as logging]
    [clojure.walk :refer [keywordize-keys]]
    [logbug.debug :as debug]
    [madek.api.utils.sql :as sql]
    ))

(def DEFAULT_LIMIT 100)

(defn page-number [params]
  (let [page (or (-> params :page) 0)]
    (if (> page 0) page 0)))
  ;(let [page (-> params keywordize-keys :page)]
  ;  (if (not= nil page)
  ;    (let [pagen (Integer/parseInt page)]
  ;      (or pagen 0))
  ;    0)
  ;  )
  ;)

(defn page-count [params]
  (let [count (or (-> params :count) DEFAULT_LIMIT)]
    (if (> count 0) count DEFAULT_LIMIT)))

(defn compute-offset [params]
  (* (page-count params) (page-number params)))

(defn add-offset-for-honeysql [query params]
  (let [off (compute-offset params)]
    (-> query
        (sql/offset off)
        (sql/limit (page-count params)))))

(defn next-page-query-query-params [query-params]
  (let [query-params (keywordize-keys query-params)
        i-page (page-number query-params)]
    (assoc query-params
           :page (+ i-page 1))))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
