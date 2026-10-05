(ns madek.api.resources.locales
  (:require
   [madek.api.db.settings :as settings]
   [madek.api.utils.config :refer [get-config]]))

(defn default-locale
  "App-settings default locale, then config, then \"de\"."
  [tx]
  (or (when tx
        (some-> (settings/settings tx) :default_locale not-empty))
      (some-> (get-config) :madek_default_locale not-empty)
      "de"))

(defn- add-field-for-default-locale
  "Copy plural hstore field (`labels` → `label`) for `locale`."
  [result field-name locale]
  (let [plural (get result (keyword (str field-name "s")))
        loc (keyword (or locale "de"))]
    (assoc result (keyword field-name)
           (when (map? plural)
             (or (get plural loc)
                 (get plural (name loc)))))))

(defn add-fields-for-default-locale
  "Singular `:label`, `:description`, and `:hint` for the default locale (same as `/api`)."
  [result locale]
  (-> result
      (add-field-for-default-locale "label" locale)
      (add-field-for-default-locale "description" locale)
      (add-field-for-default-locale "hint" locale)))
