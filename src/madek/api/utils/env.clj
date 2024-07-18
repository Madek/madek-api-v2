(ns madek.api.utils.env)

(defn- pr
  [env-var v]
  (println ">o> " env-var v)
  v)

(defn str-to-bool [s default]
  (case (clojure.string/lower-case s)
    "true" true
    "false" false
    "1" true
    "0" false
    "yes" true
    "no" false
    default))  ; default to false if none of the above match

(defn get-env
  [env-var default-value]
  (pr "getEvnStr _> " (or (System/getenv env-var) default-value)))
(defn get-env-bool
  [env-var default-value]
  (pr env-var (str-to-bool (get-env env-var default-value) default-value)))