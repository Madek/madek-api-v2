(ns madek.api.authentication.rproxy-basic
  (:require [cider-ci.open-session.bcrypt :refer [checkpw hashpw]]
   ;[bcrypt-clj.core :as bcrypt]
            [clojure.java.io :as io]
            [clojure.string :as str])
  )


;; Read the htpasswd file and store it in a def

;(def htpasswd-data
;  (let [lines (slurp htpasswd-file)
;        p (println ">o> lines=" lines)
;
;        a (checkpw "Madek" lines )
;        p (println ">o> a=" a)
;
;        ]
;    ;(into {}
;    ;  (map (fn [line]
;    ;         (let [[username password-hash] (str/split line #":" 2)]
;    ;           [username password-hash]))
;    ;    (str/split-lines lines)))
;
;    ))
;


(defn read-htpasswd [filepath]
  (with-open [reader (io/reader filepath)]
    (into {} (map (fn [line]
                    (let [[user pass] (str/split line #":" 2)]
                      [user (str/trim pass)]))
               (line-seq reader)))))

(def passwords (read-htpasswd "/etc/madek/madek.htpasswd"))

(defn sha256 [s]
  (let [digest (MessageDigest/getInstance "SHA-256")
        hash-bytes (.digest digest (.getBytes s "UTF-8"))]
    (apply str (map (fn [b] (format "%02x" b)) hash-bytes))))

(defn authenticate [username password]
  (let [stored-password (get passwords username)
        hashed-password (sha256 password)]
    (and stored-password
      (= stored-password hashed-password))))


;; add main and command to test the function
(defn -main [& args]
  (let [username (first args)
        password (second args)

        ab (authenticate "Madek" "Madek")
        cd (authenticate "Madek1" "Madek")
        p (println ">o> ab=" ab)
        p (println ">o> cd=" cd)


        ;data (slurp "/etc/madek/madek.htpasswd")
        data (slurp "/Users/mradl/repos/Madek/api-v2/src/madek/api/authentication/madek.htpasswd")

        ;p (println ">o> passwords=" passwords)
        ;data passwords

        ;ef (checkpw "Madek" data )
        ;ef (checkpw "$2y$05$7qLMOupe6xVmisTxaczkGeDTxGP4lUT88WWGs2yDTcQ6u5rdRmj2a" data )
        ;ef (checkpw "Madek:$apr1$iTbPb4GR$Ezce.FX300GODRc9dLTDt." data )
        ef (checkpw (hashpw "Madek") data)
        p (println ">o> ef=" ef)

        ]



    (println ">o> servus")


    ;(if (authenticate username password)
    ;  (println "Authenticated")
    ;  (println "Not authenticated"))

    ))