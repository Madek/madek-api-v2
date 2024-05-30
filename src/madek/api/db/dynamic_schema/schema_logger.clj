(ns madek.api.db.dynamic_schema.schema_logger
  (:require
   [clojure.pprint :refer [pprint]]

   [taoensso.timbre :refer [info]]))

(def ENABLE_SCHEMA_LOGGING false)

(defn slog
  ([msg]
   (if ENABLE_SCHEMA_LOGGING
     (info (str msg "\n"))))

  ([msg map]
   (if ENABLE_SCHEMA_LOGGING
     (info (str msg "\n")
           (pprint map)))))
