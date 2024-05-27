(ns madek.api.utils.sql-next)

(defn convert-sequential-values-to-sql-arrays [m]
  "For every sequential value insert [:array [..]] so HoneySQL will transform
  this in a proper query to update PG-arrays"
  (->> m
       (map (fn [[k v]]
              [k (if (sequential? v)
                   [:array v]
                   v)]))
       (into {})))
