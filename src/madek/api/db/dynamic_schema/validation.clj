(ns madek.api.db.dynamic_schema.validation
  (:require
   [clojure.string :as str]
   [madek.api.db.dynamic_schema.common :refer [add-to-validation-cache]]
   [taoensso.timbre :refer [error]]))

(defn extract-column-names [db-meta]
  (cond
    (or (instance? clojure.lang.PersistentVector db-meta) (instance? clojure.lang.LazySeq db-meta))
    (set (map :column_name (into [] db-meta)))

    (or (instance? clojure.lang.PersistentHashMap db-meta) (instance? clojure.lang.PersistentArrayMap db-meta))
    (into #{} (map (comp name first) db-meta))

    :else
    (do
      (error "ERROR: Something failed, missing handler for db-meta.type=" (class db-meta))
      (into #{} (map (comp name first) db-meta)))))

(defn validate-if-keys-exist? [db-meta keys debug-info]
  (let [existing-keys (extract-column-names db-meta)
        keys (map name keys)
        missing-keys (filter #(not (contains? existing-keys %)) keys)]

    (when (not (empty? missing-keys))
      (let [error-msg (str "ERROR: Incorrect definition of key(s): " (str/join ", " missing-keys))
            debug-info (update debug-info :error conj error-msg)]
        (add-to-validation-cache debug-info)))

    (empty? missing-keys)))

(defn validate-keys [table-name db-meta wl-attr bl-attr context]
  (let [debug-info {:context context
                    :table-name table-name
                    :db-meta db-meta}

        is-wl-valid (when wl-attr
                      (validate-if-keys-exist? db-meta wl-attr (assoc debug-info :validation-type ":wl / whitelist")))

        is-bl-valid (when bl-attr
                      (validate-if-keys-exist? db-meta bl-attr (assoc debug-info :validation-type ":bl / blacklist")))]))
