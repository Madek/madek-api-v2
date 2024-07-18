(ns madek.api.authentication.test
  )

(defn get-env
  [env-var default-value]
  (or (System/getenv env-var) default-value))
(defn get-env-bool
  [env-var default-value]
  (boolean (get-env env-var default-value))
  )


(defn -main [& args]

  (let [
        my-var (get-env "MY_ENV_VAR" "default-value")
        _ (println "running main >>" my-var)
        _ (println "running main >>" (type my-var))

        my-var (get-env-bool "MY_ENV_VAR" false)
        _ (println "running main >>" my-var)
        _ (println "running main >>" (type my-var))

        ])
  )







;(or (presence (env :madek-env))