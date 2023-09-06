FROM clojure
COPY . /usr/src/app
WORKDIR /usr/src/app
CMD [. /bin/clj-run]