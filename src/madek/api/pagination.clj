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

(defn sql-offset-and-limit [query params]
  (let [params (if (contains? params :size)
                 (assoc params :count (:size params))
                 params)

        defaults {:page 0 :count 100}
        params (merge defaults params)
        off (compute-offset params)
        limit (page-count params)]
    (-> query
        (sql/limit limit)
        (sql/offset off))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
