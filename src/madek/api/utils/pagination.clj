(ns madek.api.utils.pagination
  (:require
   [clojure.walk :refer [keywordize-keys]]
   [honey.sql.helpers :as sql]
   [madek.api.utils.helper :refer [parse-specific-keys]]))

(def DEFAULT_LIMIT 1000)

(defn page-number [params]
  (let [page (or (-> params :page) 0)]
    (if (> page 0) page 0)))

(defn page-count [params]
  (let [count (or (-> params :count) DEFAULT_LIMIT)]
    (if (> count 0) count DEFAULT_LIMIT)))

(defn compute-offset [params]
  (* (page-count params) (page-number params)))

(defn sql-offset-and-limit [query params] "Caution: zero-based page numbers"
  (let [defaults {:page 0 :count DEFAULT_LIMIT}
        params (merge defaults params)
        params (parse-specific-keys params defaults)
        off (compute-offset params)
        limit (page-count params)]
    (-> query
        (sql/offset off)
        (sql/limit limit))))

(defn next-page-query-query-params [query-params]
  (let [query-params (keywordize-keys query-params)
        i-page (page-number query-params)]
    (assoc query-params
           :page (+ i-page 1))))

;### Debug ####################################################################

;; create fnc defaults: page/size-value, required
;
;{:page-val "0"
; :size-val "11"
; :page-required true
;  :size-required true}
;
;
;{:parameters [{:name        "page"
;               :in          "query"
;               :description "Page number, defaults to 0 (zero-based)"
;               :required    true
;               :value       "0"
;               }
;              {:name        "size"
;               :in          "query"
;               :description "Number of items per page, defaults to 10"
;               :required    true
;               :value       "11"}]}



;### Debug ####################################################################
;(debug/debug-ns *ns*)
