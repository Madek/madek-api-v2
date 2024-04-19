(ns madek.api.resources.locales
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.utils.config :refer [get-config]]
   [next.jdbc :as jdbc]))

;(defn- find-app-setting
;  [ds]
;  (let [query (-> (sql/select :*)
;                  (sql/from :app_settings)
;                  (sql-format))]
;    (jdbc/execute-one! ds query)))
;
;(defn- default-locale
;  [ds]
;  (let [app-setting (find-app-setting ds)]
;    (if-not (nil? app-setting)
;      (:default_locale app-setting)
;      (let [config (get-config)]
;        (:madek_default_locale config)))))
;
;(defn add-field-for-default-locale
;  [field-name result ds]
;  (let [field-plural (keyword (str field-name "s"))
;        field-name (keyword field-name)]
;    (assoc result field-name (get-in result [field-plural (default-locale ds)]))))
