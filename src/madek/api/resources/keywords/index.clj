(ns madek.api.resources.keywords.index
  (:require
    [clojure.java.jdbc :as jdbc]
    [madek.api.resources.meta-keys.meta-key :as meta-key]
    [madek.api.utils.rdbms :as rdbms :refer [get-ds]]
    [madek.api.utils.sql :as sql]
    ))

(defn get-index [meta-datum]
  (let [meta-key (first (jdbc/query (rdbms/get-ds)
                                    (meta-key/build-meta-key-query (:meta_key_id meta-datum))))
        query-base (-> (sql/select :keywords.*)
                       (sql/from :keywords)
                       (sql/merge-join
                         :meta_data_keywords
                         [:= :meta_data_keywords.keyword_id :keywords.id])
                       (sql/merge-where
                         [:= :meta_data_keywords.meta_datum_id (:id meta-datum)]))
        query (sql/format (cond-> query-base
                            (:keywords_alphabetical_order meta-key)
                            (sql/order-by [:keywords.term :asc])))]
    (jdbc/query (get-ds) query)))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
