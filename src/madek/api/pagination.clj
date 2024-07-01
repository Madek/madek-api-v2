(ns madek.api.pagination
  (:require
   [clojure.walk :refer [keywordize-keys]]
   [honey.sql.helpers :as sql]))

(def DEFAULT_LIMIT 1000)

(defn page-number [params]
  (let [page (or (-> params :page) 0)]
    (if (> page 0) page 0)))

(defn page-count [params]
  (let [count (or (-> params :count) DEFAULT_LIMIT)]
    (if (> count 0) count DEFAULT_LIMIT)))

(defn compute-offset [params]
  (* (page-count params) (page-number params)))

(defn add-offset-for-honeysql [query params]
  (let [p (println ">o> query=" query)
        p (println ">o> params=" params)
        p (println ">o> params=" (type (:page params)))
        p (println ">o> params=" (type (:count params)))
        p (println ">o> params=" (type (:size params)))

        params (if (contains? params :size)
                 (assoc params :count (:size params))
                 params)

        ;;; cast count and size to int
        ;params (assoc params :count (Integer/parseInt (str (:count params))))
        ;params (assoc params :size (Integer/parseInt (str (:size params))))

        defaults {:page 0 :count 100}
        params (merge defaults params)
        off (compute-offset params)
        limit (page-count params)]
    (-> query
        (sql/limit limit)
        (sql/offset off))))

(defn next-page-query-query-params [query-params]
  (let [query-params (keywordize-keys query-params)
        i-page (page-number query-params)]
    (assoc query-params
           :page (+ i-page 1))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
