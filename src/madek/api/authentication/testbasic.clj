(ns madek.api.authentication.testbasic
  (:require [clojure.java.shell :refer [sh]]))

;(defn md5-crypt [password salt]
;  "Generate an MD5 Crypt hash for the given password and salt."
;  (Md5Crypt/md5Crypt (.getBytes password) salt))
;
;(defn verify-md5-crypt-password [password md5-crypt-hash]
;  "Verifies if the given password matches the MD5 Crypt hash."
;  (let [salt (re-find #"\$apr1\$\w+" md5-crypt-hash)
;        hashed (md5-crypt password salt)]
;    (= hashed md5-crypt-hash)))
;
;(defn -main [& args]
;  (let [password "test"
;        md5-crypt-hash "$apr1$EtuvpScm$sMLTA.JaMN9eJKVhjqQCQ0"]
;    (println (verify-md5-crypt-password password md5-crypt-hash))))  ; Should print true or false
;
;;; Uncomment the following line if you want to run the main function automatically
;;; (-main)


;(ns madek.api.authentication.shell
;  (:require [clojure.java.shell :refer [sh]]))

;(defn verify-htpasswd [htpasswd-file username password]
;  "Verifies if the given username and password match the entries in the htpasswd file."
;  (let [{:keys [exit out err]} (sh "htpasswd" "-vb" htpasswd-file username password)]
;    (if (= exit 0)
;      true
;      false)))
;
;(defn -main [& args]
;  (let [
;        htpasswd-file "madek.htpasswd"
;        htpasswd-file "/Users/mradl/repos/Madek/api-v2/src/madek/api/authentication/madek.htpasswd"
;        username "test2"
;        password "test"]
;    (println (verify-htpasswd htpasswd-file username password))))  ; Should print true or false

;; Uncomment the following line if you want to run the main function automatically
;; (-main)



;(ns madek.api.authentication.shell
;  (:require [clojure.java.shell :refer [sh]]))

(defn verify-htpasswd [htpasswd-file username password]
  "Verifies if the given username and password match the entries in the htpasswd file."
  (let [{:keys [exit out err]} (sh "htpasswd" "-vb" htpasswd-file username password)]
    {:exit exit
     :out out
     :err err}))

(defn -main [& args]
  (let [htpasswd-file "madek.htpasswd"
        htpasswd-file "/Users/mradl/repos/Madek/api-v2/src/madek/api/authentication/madek.htpasswd"

        username "test2"
        password "test"
        result (verify-htpasswd htpasswd-file username password)]
    (println "Exit code:" (:exit result))
    (println "Output:" (:out result))
    (println "Error:" (:err result))
    ;(System/exit (:exit result))
    ))                                                      ; Ensure the process ends with the appropriate exit code

;; Uncomment the following line if you want to run the main function automatically
;; (-main)
