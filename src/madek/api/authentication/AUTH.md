# Auth/Verification

Verify db-password (bcypt)
--

- Prefix: $2y$
- Algorithm: Bcrypt

```bash
htpasswd -bB madek.htpasswd test test             
> Adding password for user test

htpasswd -vB madek.htpasswd Madek      
> Enter password:
> Password for user Madek correct.

htpasswd -vbB madek.htpasswd test test
> Password for user test correct.
```

```clojure

(let [;; db-basic-auth: auth_systems_users.data / Bcrypt ($2y$)
      db-hash (checkpw "test" "$2a$10$Qa8Mvdwg1KpSqFvvwoex7ec4zl0PfStw9SrMIy8S5g9/P37XDssEG")
      _ (println ">o> db-hash=" db-hash)])
```


---


Verify db-password (md5)
--
- MD5 Crypt ($apr1$)
```bash
htpasswd -b madek.htpasswd test test
> Adding password for user test

htpasswd -vb madek.htpasswd test test  
> Password for user test correct.

test:$apr1$EtuvpScm$sMLTA.JaMN9eJKVhjqQCQ0
```

```clojure
  (let [;; rproxy basic-auth / MD5 Crypt ($apr1$)
        md5-res (verify-md5-crypt "test" "$apr1$EtuvpScm$sMLTA.JaMN9eJKVhjqQCQ0")
        _ (println ">o> md5-res=" md5-res)

        ;; create adn verify md5 hash
        hash (generate-md5-crypt "Madek")
        _ (println ">o> hash=" hash)

        md5-res (verify-md5-crypt "Madek" hash)
        _ (println ">o> md5-res=" md5-res)
        ])
```