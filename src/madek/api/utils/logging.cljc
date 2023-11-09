(ns madek.api.utils.logging
  (:require
    #?(:clj
       [taoensso.timbre.tools.logging]
       [logbug.thrown :as thrown])
    [taoensso.timbre.appenders.core :as appenders]
    [taoensso.timbre :as timbre :refer [debug info]]))


(def LOGGING_CONFIG
  {:min-level [[#{
                  ;"madek.api.authentication" ;
                  ;"madek.api.resources.*"
                  ;"madek.api.web"
                  } :debug]
               [#{
                  #?(:clj "com.zaxxer.hikari.*")
                  "madek.*"} :info]
               [#{"*"} :warn]]
   :appenders #?(:clj {:spit (appenders/spit-appender {:fname "log/debug.log"})}
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
