(ns madek.api.constants
  (:require
    [clojure.string :refer [trim blank?]]
    [clojure.tools.logging :as logging]
    [environ.core :refer [env]]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [madek.api.utils.config :as config :refer [get-config]]
    [me.raynes.fs  :as clj-fs]
    ))

(def SUPPORTED_META_DATA_TYPES
  #{
      "MetaDatum::Groups" ; old: migrated to: people
      "MetaDatum::JSON"
      "MetaDatum::Keywords" ; TODO Frage: feature multi language ?
      "MetaDatum::Licenses" ; old: migrated to: keywords
      ;"MetaDatum::MediaEntry" ; TODO reintegrate
      "MetaDatum::People"
      "MetaDatum::Roles" ; are multi language via h-store
      "MetaDatum::Text"
      ; TODO future: check if multi-language implementation
      ; is possible with new API2
      ; and backwards compatible with current web-app
      ; and does not require sql schema changes.
      ;"MetaDatum::TextML"
      "MetaDatum::TextDate"
      "MetaDatum::Users" ; old: migrated to: people
      "MetaDatum::Vocables" ; old: migrated to: keywords
    })

(declare DEFAULT_STORAGE_DIR
         FILE_STORAGE_DIR
         THUMBNAILS_STORAGE_DIR )

(defn presence [v]
  "Returns nil if v is a blank string. Returns v otherwise."
  (cond
    (string? v) (if (clojure.string/blank? v) nil v)
    :else v))

(defn- madek-env []
  (or (presence (env :madek-env))
      (presence (env :rails-env))
      (do (logging/warn "neither MADEK_ENV nor RAILS_ENV is not set; using test")
          "test")))

(defn initialize [config]

  (def DEFAULT_STORAGE_DIR
    (str (clj-fs/absolute
           (or (:default_storage_dir (get-config))
               (clojure.string/join (java.io.File/separator)
                                    [(System/getProperty "user.dir") "tmp" (madek-env)])))))

  (def FILE_STORAGE_DIR
    (str (clj-fs/absolute
           (or (:file_storage_dir (get-config))
               (clojure.string/join (java.io.File/separator)
                                    [DEFAULT_STORAGE_DIR "originals"])))))
  (def THUMBNAILS_STORAGE_DIR
    (str (clj-fs/absolute
           (or (:thumbnail_storage_dir (get-config))
               (clojure.string/join (java.io.File/separator)
                                    [DEFAULT_STORAGE_DIR "thumbnails"])))))

  (logging/info
    {:DEFAULT_STORAGE_DIR DEFAULT_STORAGE_DIR
     :FILE_STORAGE_DIR FILE_STORAGE_DIR
     :THUMBNAILS_STORAGE_DIR THUMBNAILS_STORAGE_DIR})
  )


;### Debug ####################################################################
;(debug/debug-ns *ns*)
