(ns madek.api.cli_config_parser
  (:gen-class)
  (:require
   [cuerdas.core :refer [kebab snake upper]]
   [environ.core :refer [env]]
   [logbug.thrown]
   [madek.api.constants]
   [madek.api.http.server :as http-server]
   [pg-types.all]))

(def http-resources-scope-key :http-resources-scope)

(defn long-opt-for-key [k]
  (str "--" (kebab k) " " (-> k snake upper)))

(def cli-options
  (concat http-server/cli-options
          [[nil (long-opt-for-key http-resources-scope-key)
            "Either ALL, ADMIN or USER"
            :default (or (some-> http-resources-scope-key env)
                         "ALL")
            :validate [#(some #{%} ["ALL" "ADMIN" "USER"]) "scope must be ALL, ADMIN or USER"]]]))



