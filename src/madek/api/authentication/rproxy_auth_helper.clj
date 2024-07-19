(ns madek.api.authentication.rproxy-auth-helper
  (:require
   [buddy.core.codecs :refer :all]
   [clojure.string :as str]
   [digest :refer [md5]])
  (:import
   (java.io FileNotFoundException)
   (org.apache.commons.codec.digest Md5Crypt)))

(defn- read-file-or-default [file-path default-value]
  (try
    (str/split-lines (slurp file-path))
    (catch FileNotFoundException e
      default-value)))

(def rproxy-auth-data (read-file-or-default "/etc/madek/madek.htpasswd" []))

(defn- verify-md5-crypt [password hash]
  (let [[_ _ salt] (str/split hash #"\$" 4)
        salt (str "$apr1$" salt)
        computed-hash (Md5Crypt/apr1Crypt (.getBytes password "UTF-8") salt)]
    (= computed-hash hash)))

(defn- read-rproxy-htpasswd []
  (let [lines rproxy-auth-data]
    (into {} (map (fn [line]
                    (let [[username hash] (str/split line #":" 2)]
                      {username hash}))
                  lines))))

(defn verify-password [username password]
  (let [user-creds (read-rproxy-htpasswd)
        hash (get user-creds username)]
    (if hash
      (verify-md5-crypt password hash)
      false)))
