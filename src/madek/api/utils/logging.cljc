(ns madek.api.utils.logging
  (:require
   #?(:clj
      [taoensso.timbre.tools.logging]
      [logbug.thrown :as thrown])
   [clojure.walk]
   [taoensso.timbre :as timbre :refer [debug info]]
   [taoensso.timbre.appenders.core :as appenders]))

(defn clean-request
  "Recursively removes some keys and values from the request map.
  An optional second argument specifies the keys, the default
  is [:data :middleware]. "
  ([req]
   (clean-request req [:data :middleware]))
  ([req ks]
   (clojure.walk/prewalk
    (fn [x]
      (if (map? x)
        (apply dissoc (concat [x] ks))
        x))
    req)))

(def LOGGING_CONFIG
  {:min-level [[#{;"madek.api.*"
                  ;"madek.api.authentication" ;
                  "madek.api.resources.*"
                  "madek.api.resources.people.*"
                  ;"madek.api.resources.users.*"
                  ;"madek.api.resources.users.create"
                  ;"madek.api.web"
                  }:debug]
               [#{#?(:clj "com.zaxxer.hikari.*")
                  "madek.*"} :info]
               [#{"*"} :warn]]
   :appenders #?(:clj {} ;{:spit (appenders/spit-appender {:fname "log/debug.log"})}
                 :cljs {})
   :log-level nil})

(defn init
  ([] (init LOGGING_CONFIG))
  ([logging-config]
   (info "initializing logging " logging-config)
   (timbre/merge-config! logging-config)
   #?(:clj
      (taoensso.timbre.tools.logging/use-timbre)
      (thrown/reset-ns-filter-regex #"madek"))
   (info "initialized logging " (pr-str timbre/*config*))))
