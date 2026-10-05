(ns madek.api.resources.locales
  (:require
   [madek.api.db.settings :as settings]
   [madek.api.utils.config :refer [get-config]]))

(defn default-locale
  "App-settings default locale, then config, then `de`."
  ([tx]
   (or (some-> (settings/settings tx) :default_locale str not-empty)
       (some-> (get-config) :madek_default_locale str not-empty)
       "de"))
  ([]
   (or (some-> (get-config) :madek_default_locale str not-empty)
       "de")))

(defn add-field-for-default-locale
  "Copy plural hstore/map field (`labels` → `label`) for the default locale."
  ([field-name result locale]
   (let [field-plural (keyword (str field-name "s"))
         field-name (keyword field-name)
         loc (keyword (or locale "de"))]
     (assoc result field-name (get-in result [field-plural loc]))))
  ([field-name result]
   (add-field-for-default-locale field-name result (default-locale))))

(defn add-fields-for-default-locale
  "Same as `/api`: singular `:label`, `:description`, `:hint` from the default locale."
  ([result locale]
   (-> result
       (add-field-for-default-locale "label" locale)
       (add-field-for-default-locale "description" locale)
       (add-field-for-default-locale "hint" locale)))
  ([result]
   (add-fields-for-default-locale result (default-locale))))
