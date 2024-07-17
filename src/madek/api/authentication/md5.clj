(ns madek.api.authentication.md5
  (:require
    [cider-ci.open-session.bcrypt :refer [checkpw]]
   ; ;[buddy.core.codecs :as codecs]
   ; [buddy.core.hash :as hash]

   [buddy.core.codecs :refer :all]
   [clojure.string :as str]
   [digest :refer [md5]]
   )
  (:import
   (org.apache.commons.codec.digest Md5Crypt))
  )

(defn verify-md5-crypt [password hash]
  (let [[_ _ salt] (str/split hash #"\$" 4)                 ; Split the hash and extract the salt
        salt (str "$apr1$" salt)                            ; Reconstruct the salt with the apr1 prefix
        computed-hash (Md5Crypt/apr1Crypt (.getBytes password "UTF-8") salt)] ; Compute the hash using UTF-8 encoding
    (= computed-hash hash)))                                ; Compare the computed hash with the given hash


;; create main
(defn -main [& args]
  (let [
        ;; db-basic-auth: auth_systems_users.data
        db-hash (checkpw "test" "$2a$10$Qa8Mvdwg1KpSqFvvwoex7ec4zl0PfStw9SrMIy8S5g9/P37XDssEG")
        p (println ">o> db-hash=" db-hash)

        ;; rproxy basic-auth
        md5-res (verify-md5-crypt "test" "$apr1$EtuvpScm$sMLTA.JaMN9eJKVhjqQCQ0")
        p (println ">o> md5-res=" md5-res)
        ])
  )
