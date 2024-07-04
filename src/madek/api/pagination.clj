(ns madek.api.pagination
  (:require
   [honey.sql.helpers :as sql]))

;; used if count has invalid value (negative or 0)
(def DEFAULT_LIMIT 1000)

(def ZERO_BASED_PAGINATION false)
(def DEFAULT_COUNT_SWAGGER 100)
(def DEFAULT_PAGE_SWAGGER (if ZERO_BASED_PAGINATION 0 1))

(defn page-number [params]
  (let [page (or (-> params :page) 0)]
    (if (> page 0) page 0)))

(defn page-count [params]
  (let [count (or (-> params :count) DEFAULT_LIMIT)]
    (if (> count 0) count DEFAULT_LIMIT)))

(defn compute-offset [params]
  (* (page-count params) (page-number params)))

(defn- normalize-params
  "- rename :size to :count,
  - decreases ZERO_BASED_PAGINATION==false
  - set default-values if not present, DEFAULT_COUNT=100
  "
  [params]
  (let [params (if (contains? params :size)
                 (assoc params :count (:size params))
                 params)
        defaults (if ZERO_BASED_PAGINATION
                   {:page 0 :count DEFAULT_COUNT_SWAGGER}
                   {:page 1 :count DEFAULT_COUNT_SWAGGER})
        params (merge defaults params)
        params (if (not ZERO_BASED_PAGINATION)
                 (update params :page #(if (pos? %) (dec %) 0))
                 params)]
    params))

(defn sql-offset-and-limit [query params]
  (let [params (normalize-params params)
        off (compute-offset params)
        limit (page-count params)]
    (-> query
        (sql/limit limit)
        (sql/offset off))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
