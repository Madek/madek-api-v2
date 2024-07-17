
Pagination / schema validation
--
```clojure
sudo mkdir -p /etc/madek
sudo ln -s /Users/mradl/repos/Madek/api-v2/config /etc/madek/madek.htpasswd

touch /etc/madek/madek.htpasswd
cat /etc/madek/madek.htpasswd
```


Madek1:$2y$05$7qLMOupe6xVmisTxaczkGeDTxGP4lUT88WWGs2yDTcQ6u5rdRmj2a
Madek:$apr1$iTbPb4GR$Ezce.FX300GODRc9dLTDt.


Madek


Madek1:$2y$05$7qLMOupe6xVmisTxaczkGeDTxGP4lUT88WWGs2yDTcQ6u5rdRmj2a
Madek:$apr1$iTbPb4GR$Ezce.FX300GODRc9dLTDt.


clojure -m madek.api.authentication.rproxy-basic

Pagination / schema validation
--
```clojure
sudo htpasswd -bB /etc/madek/madek.htpasswd Madek Madek

```