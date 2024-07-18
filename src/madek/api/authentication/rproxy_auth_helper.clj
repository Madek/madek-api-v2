(ns madek.api.authentication.rproxy-auth-helper
  (:require
   [buddy.core.codecs :refer :all]
   [clojure.string :as str]
   [digest :refer [md5]]
   )
  (:import
   (java.io FileNotFoundException)
   (org.apache.commons.codec.digest Md5Crypt))
  )

(defn- read-file-or-default [file-path default-value]
  (try
    (str/split-lines (slurp file-path))
    (catch FileNotFoundException e
      default-value)))

(def rproxy-auth-data (read-file-or-default "/etc/madek/madek.htpasswd" []))

(defn- verify-md5-crypt [password hash]
  (let [[_ _ salt] (str/split hash #"\$" 4)                 ; Split the hash and extract the salt
        salt (str "$apr1$" salt)                            ; Reconstruct the salt with the apr1 prefix
        computed-hash (Md5Crypt/apr1Crypt (.getBytes password "UTF-8") salt)] ; Compute the hash using UTF-8 encoding
    (= computed-hash hash)))                                ; Compare the computed hash with the given hash

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




;; generator: https://8gwifi.org/htpasswd.jsp
(defn -main [& args]
  (let [
        ;;; create and verify bcrypt hash
        ;db-hash (hashpw "meins")
        ;_ (println ">o> db-hash1=" db-hash)
        ;db-hash (hashpw "meins")
        ;_ (println ">o> db-hash2=" db-hash)
        ;
        ;db-hash (checkpw "meins" db-hash)
        ;_ (println ">o> Verify 'meins' =>" db-hash)
        p (println ">o> data=" rproxy-auth-data)

        res (verify-password "test2" "test")
        p (println ">o> res=" res)

        ;res (verify-password "test" "Madek" "/Users/mradl/repos/Madek/api-v2/src/madek/api/authentication/madek.htpasswd")
        ;p (println ">o> res=" res)

        ;;; db-basic-auth: auth_systems_users.data / Bcrypt ($2y$)
        ;db-hash (checkpw "test" "$2a$10$Qa8Mvdwg1KpSqFvvwoex7ec4zl0PfStw9SrMIy8S5g9/P37XDssEG")
        ;_ (println ">o> Verify 'test' =>" db-hash)
        ;
        ;;; rproxy basic-auth / MD5 Crypt ($apr1$)
        ;md5-res (verify-md5-crypt "test" "$apr1$EtuvpScm$sMLTA.JaMN9eJKVhjqQCQ0")
        ;_ (println ">o> Verify 'test' =>" md5-res)
        ;md5-res (verify-md5-crypt "Madek" "$apr1$V0tgjCf4$Mx7BIKZbpc3BRyHd7sqZW/")
        ;_ (println ">o> Verify 'Madek' =>" md5-res)
        ;
        ;
        ;;; create adn verify md5 hash
        ;hash (generate-md5-crypt "Madek")
        ;_ (println ">o> hash=" hash)
        ;
        ;md5-res (verify-md5-crypt "Madek" hash)
        ;_ (println ">o> Verify 'Madek' =>" md5-res)
        ])
  )
