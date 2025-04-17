(ns madek.api.db.core
  (:require
   [cheshire.core :as json]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [environ.core :refer [env]]
   [madek.api.db.type-conversion]
   [madek.api.utils.cli :refer [long-opt-for-key]]
   [next.jdbc :as jdbc]
   [next.jdbc.connection :as connection]
   [next.jdbc.result-set :as jdbc-rs]
   [taoensso.timbre :refer [debug info warn]])
  (:import
   (com.zaxxer.hikari HikariDataSource)
   [java.io ByteArrayInputStream]
   ))

(defonce ^:private ds* (atom nil))

(def builder-fn-options-default
  {:builder-fn jdbc-rs/as-unqualified-lower-maps})

(defn get-ds []
  (jdbc/with-options @ds* builder-fn-options-default))

;;; CLI ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def db-name-key :db-name)
(def db-port-key :db-port)
(def db-host-key :db-host)
(def db-user-key :db-user)
(def db-password-key :db-password)
(def db-min-pool-size-key :db-min-pool-size)
(def db-max-pool-size-key :db-max-pool-size)
(def options-keys [db-name-key db-port-key db-host-key
                   db-user-key db-password-key
                   db-min-pool-size-key db-max-pool-size-key])

(def cli-options
  [[nil (long-opt-for-key db-name-key) "Database name, falls back to PGDATABASE | madek"
    :default (or (some-> db-name-key env)
               (some-> :pgdatabase env)
               "madek")]
   [nil (long-opt-for-key db-port-key) "Database port, falls back to PGPORT or 5415"
    :default (or (some-> db-port-key env Integer/parseInt)
               (some-> :pgport env Integer/parseInt)
               5415)
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be an integer between 0 and 65536"]]
   [nil (long-opt-for-key db-host-key) "Database host, falls back to PGHOST | localhost"
    :default (or (some-> db-host-key env)
               (some-> :pghost env)
               "localhost")]
   [nil (long-opt-for-key db-user-key) "Database user, falls back to PGUSER | 'madek'"
    :default (or (some-> db-user-key env)
               (some-> :pguser env)
               "madek")]
   [nil (long-opt-for-key db-password-key) "Database password, falls back to PGPASSWORD |'madek'"
    :default (or (some-> db-password-key env)
               (some-> :pgpassword env)
               "madek")]
   [nil (long-opt-for-key db-min-pool-size-key)
    :default (or (some-> db-min-pool-size-key env Integer/parseInt)
               2)
    :parse-fn #(Integer/parseInt %)]
   [nil (long-opt-for-key db-max-pool-size-key)
    :default (or (some-> db-max-pool-size-key env Integer/parseInt)
               16)
    :parse-fn #(Integer/parseInt %)]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn extract-data-from-input-stream [input-stream]
  (when (instance? java.io.ByteArrayInputStream input-stream)
    (slurp input-stream)))

(defn pretty-print-json [json-str]
  (json/generate-string (json/parse-string json-str true) {:pretty true}))

(defn fetch-data [m]
  (some-> m :reitit.core/match :template))

(defn contains-substrings? [s substrings]
  (and s (every? #(re-find (re-pattern (java.util.regex.Pattern/quote %)) s) substrings)))


(defn has-coercion-substring? [s]
  (boolean (re-find #"\"coercion\"\s*:\s*\"(spec|schema)\"" s)))



(defn parse-edn-strings [m]
  (clojure.walk/postwalk
    (fn [x]
      (if (and (string? x)
            (re-find #"^\(" x))                             ;; crude check for EDN-ish string
        (try
          (edn/read-string x)
          (catch Exception _ x))
        x))
    m))

(defn beautify-problems [problems]
  (map (fn [problem]
         (-> problem
             (dissoc :path :via)
             (update :in #(str/join "/" %))))
    problems))
(require '[cheshire.core :as json]
  '[clojure.string :as str])



(defn data->input-stream [data]
  (-> data
      (json/generate-string)
      (.getBytes "UTF-8")
      (ByteArrayInputStream.)))


(defn is-coercion-error? [data]
  (or (contains-substrings? data ["schema" "errors" "type" "coercion" "value" "in"])
    (contains-substrings? data ["problems"])))

(defn extract-coercion-reason
  ([data req]
   (extract-coercion-reason data req true))

  ([data req with-errors?]
   (let [parsed-data   (-> data (json/parse-string true) parse-edn-strings)
         coercion      (:coercion parsed-data)
         is-coercion?  (some #{"spec" "schema"} [coercion])]
     (when is-coercion?
       (let [errors        (or (get-in parsed-data [:errors])
                             (beautify-problems (:problems parsed-data)))
             scope         (some->> (:in parsed-data) (map str) (str/join "/"))
             status        (if (str/includes? scope "response") 501 400)
             base-resp     {:reason         "Coercion-Error"
                            :scope          scope
                            :coercion-type  coercion
                            :uri            (str (str/upper-case (name (:request-method req)))
                                                 " " (:uri req))}
             full-resp     (if with-errors?
                             (assoc base-resp :errors errors)
                             base-resp)]
         {:is-coercion-error true
          :response-status   status
          :response-data     full-resp})))))




(defn extract-coercion-reason-without-errors [data req]
  (extract-coercion-reason data req false))

(defn generate-coercion-response [data req resp]
  (warn (pretty-print-json data))
  (let [{:keys [response-status response-data]}
        (extract-coercion-reason data req false)]
    (assoc resp
           :body   (data->input-stream response-data)
           :status response-status)))

(defn wrap-tx [handler]
  (fn [request]
    (jdbc/with-transaction [tx @ds*]
      (try
        (let [tx-with-opts (jdbc/with-options tx builder-fn-options-default)
              resp         (handler (assoc request :tx tx-with-opts))
              error?       (and (:status resp) (>= (:status resp) 400) (:body resp))]
          (if error?
            (do
              (warn "Rolling back transaction because error status " (:status resp))
              (warn "   Details: " (clojure.string/upper-case (name (:request-method request))) (fetch-data request))
              (.rollback tx)
              (let [ext-data (extract-data-from-input-stream (:body resp))]
                (if (and (has-coercion-substring? ext-data)
                      (is-coercion-error? ext-data))
                  (generate-coercion-response ext-data request resp)
                  resp)))
            resp))
        (catch Throwable th
          (warn "Rolling back transaction because of " (.getMessage th))
          (debug (.getMessage th))
          (.rollback tx)
          (throw th))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn close []
  (when @ds*
    (do
      (info "Closing db pool ...")
      (.close ^HikariDataSource @ds*)

      (reset! ds* nil)
      (info "Closing db pool done."))))

(defn init-ds [db-options]
  (close)
  (let [ds (connection/->pool
             HikariDataSource
             {:dbtype "postgres"
              :dbname (get db-options db-name-key)
              :username (get db-options db-user-key)
              :password (get db-options db-password-key)
              :host (get db-options db-host-key)
              :port (get db-options db-port-key)
              :maximumPoolSize (get db-options db-max-pool-size-key)
              :minimumIdle (get db-options db-min-pool-size-key)
              :autoCommit true
              :connectionTimeout 30000
              :validationTimeout 5000
              :idleTimeout (* 1 60 1000)                    ; 1 minute
              :maxLifetime (* 1 60 60 1000)                 ; 1 hour
              })]
    ;; this code initializes the pool and performs a validation check:
    (.close (jdbc/get-connection ds))
    (reset! ds* ds)
    @ds*))

(defn init
  ([options]
   (let [db-options (select-keys options options-keys)]
     (info "Initializing db " db-options)
     (init-ds db-options)
     (info "Initialized db " @ds*)
     @ds*)))
