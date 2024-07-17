
Pagination / schema validation
--
```clojure
sudo mkdir -p /etc/madek
sudo ln -s /Users/mradl/repos/Madek/api-v2/config /etc/madek/madek.htpasswd

touch /etc/madek/madek.htpasswd
cat /etc/madek/madek.htpasswd
```





test
Madek:$apr1$iTbPb4GR$Ezce.FX300GODRc9dLTDt.
Madek:$apr1$iT4567GR$Ezce.FX300123459dLTDt.









Madek1:$2y$05$7qLMOupe6xVmisTxaczkGeDTxGP4lUT88WWGs2yDTcQ6u5rdRmj2a

Madek:$apr1$iTbPb4GR$Ezce.FX300GODRc9dLTDt.


Madek

$apr1$m4icnsk5$YSNOo1lt8EFEmydXzWLUr1



Madek:$apr1$/1hTS1cM$9cCqfxPTXm6VGR2lZbvUp.


Madek1:$2y$05$7qLMOupe6xVmisTxaczkGeDTxGP4lUT88WWGs2yDTcQ6u5rdRmj2a
Madek:$apr1$iTbPb4GR$Ezce.FX300GODRc9dLTDt.


clojure -m madek.api.authentication.rproxy-basic

Pagination / schema validation
--
```clojure
sudo htpasswd -bB /etc/madek/madek.htpasswd Madek Madek

sudo htpasswd madek.htpasswd Madek Madek

```


clojure -m madek.api.authentication.rproxy_basic


=========================

auth_system_user

Bcrypt ($2y$)
Prefix: $2y$
Algorithm: Bcrypt

test:$2y$05$hGq/cmT6942GhZe/FuWyB.lBXRDmhfgKEMT5BmvFs8kJFV07zW/EK

root@NX-41294 authentication # htpasswd -bB madek.htpasswd test test             
Adding password for user test



-B  Force bcrypt encryption of the password (very secure).

root@NX-41294 authentication # htpasswd -vB madek.htpasswd Madek      
Enter password:
Password for user Madek correct.


htpasswd -vbB madek.htpasswd test test
htpasswd -vbB madek.htpasswd Madek Madek
Password for user test correct.
=======

MD5 Crypt ($apr1$)
➜  authentication git:(mr/fix-basic) ✗ htpasswd -b madek.htpasswd test2 test
Adding password for user test2

➜  authentication git:(mr/fix-basic) ✗ htpasswd -vb madek.htpasswd test2 test  
Password for user test2 correct.


test2:$apr1$EtuvpScm$sMLTA.JaMN9eJKVhjqQCQ0