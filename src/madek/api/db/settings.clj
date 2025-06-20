(ns madek.api.db.settings
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.db.type-conversion]
   [next.jdbc :as jdbc]
   [taoensso.timbre :refer [warn]]))

(def selected-columns
  [:brand_logo_url
   :brand_texts
   :site_titles
   :default_locale
   :available_locales
   :sitemap])

(defn settings [tx]
  (or (-> (apply sql/select selected-columns)
          (sql/from :app_settings)
          (sql/where [:= :id 0])
          (sql-format :inline false)
          (#(jdbc/execute-one! tx %)))
      (warn "There seem to be no (app-) settings; this instance might not be set up properly.")))

(defn wrap
  ([handler]
   (fn [request]
     (wrap handler request)))
  ([handler {tx :tx :as request}]
   (handler (assoc request :settings (settings tx)))))
