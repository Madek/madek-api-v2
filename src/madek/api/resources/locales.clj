(ns madek.api.resources.locales
  (:require
   [honey.sql :refer [format] :rename {format sql-format}]
   [honey.sql.helpers :as sql]
   [madek.api.utils.config :refer [get-config]]
   [next.jdbc :as jdbc]))

(defn- default-locale-from-db [tx]
  (when tx
    (try
      (some-> (-> (sql/select :default_locale)
                  (sql/from :app_settings)
                  (sql/where [:= :id 0])
                  sql-format
                  (#(jdbc/execute-one! tx %)))
              :default_locale
              str
              not-empty)
      (catch Exception _
        nil))))

(defn default-locale
  "App-settings default locale, then config, then `de`.
  Never throws — meta-key export must stay available if settings are missing."
  ([tx]
   (or (default-locale-from-db tx)
       (some-> (get-config) :madek_default_locale str not-empty)
       "de"))
  ([]
   (or (some-> (get-config) :madek_default_locale str not-empty)
       "de")))

(defn add-field-for-default-locale
  "Copy plural hstore/map field (`labels` → `label`) for the default locale."
  ([field-name result locale]
   (let [field-plural (keyword (str field-name "s"))
         field-kw (keyword field-name)
         loc (keyword (or locale "de"))
         plural (get result field-plural)]
     (assoc result field-kw
            (cond
              (map? plural) (or (get plural loc)
                                (get plural (name loc)))
              :else nil))))
  ([field-name result]
   (add-field-for-default-locale field-name result (default-locale))))

(defn add-fields-for-default-locale
  "Same as `/api`: singular `:label`, `:description`, `:hint` from the default locale.

  NOTE: `add-field-for-default-locale` takes `[field-name result locale]` — do not
  thread with `->` (that would put `result` in the field-name position and
  `assoc` onto a String → ClassCastException)."
  ([result locale]
   (add-field-for-default-locale
    "label"
    (add-field-for-default-locale
     "description"
     (add-field-for-default-locale
      "hint" result locale)
     locale)
    locale))
  ([result]
   (add-fields-for-default-locale result (default-locale))))
