# The Madek API2

The madek-api2 is a JSON API for Madek.


## API v2 TODOs

* Schemas:   

    * incomplete and too unspecific in many places

    * There are 2 ways of defining schema (see [COERCION.md](src/madek/api/utils/coercion/COERCION.md))
      1. **reitit.coercion.schema** (simple description of types)
      2. **reitit.coercion.spec** (more options to define swagger-ui-fields concerning default-values/description/..)

    * ⚠️ WARNING: Keep in mind to use request.parameters/body/path, not request.params (attributes won't be casted)


* Permissions:

    Some resources for public and signed in users leak to much information.

    Pagination over all entities, in particular for `users` and `people` must
    be prevented. How can we do this? Enforce query params and return only a
    fixed limit?

    A lot of open discussions here.

    We could make it part of a Madek release with per default only `/admin`
    beeing enabled and other resources only per configuration? That could
    bring us timewiese nearer  a to release of the API v2.




## Development

Requirements:

* PostgreSQL 15 Database
* `asdf` https://asdf-vm.com/
* system build tools and libaries; e.g. `sudo apt-get install build-essential` on ubuntu;
    on MacOS you will need Xcode with command line tools and further packages either from
    MacPorts or Homebrew
*  ⚠️ WARNING: local tests can fail caused by wrong order of results (see terms_for_sorting_shared_context.rb)


### Starting up the Server

    ./bin/clj-run

OpenApi: [http://localhost:3104/api-v2/api-docs/index.html](http://localhost:3104/api-v2/api-docs/index.html)


#### Running server with different scope
This will show admin-endpoints only
```bash
# options: ALL|ADMIN|USER
./bin/clj-run --http-resources-scope ADMIN

# or by env: CAUTION: snake-case
export http_resources_scope=ADMIN
```


### Running the Tests

**Rspec** should be invoked from `./bin/rspec`

```bash
./bin/rspec ./spec/resources/groups/index_spec.rb:11
```



**Clojure-tests** can be triggered manually by: (not integrated in CI)
```bash
 clojure -M:test
  
 clojure -M:test  madek.api.pagination-test.pagination-test.clj
 clojure -M:test test/*
```



### Formatting Code

#### Clojure
Use `./bin/cljfmt check` and  `./bin/cljfmt fix`.

From vim you can use `:! ./bin/cljfmt fix %` to format the current file.

#### Ruby
Use `standardrb` and  `standardrb --fix`.


### API Docs (openApi)

Swagger resource documentation http://localhost:3104/api-v2/api-docs/index.html

#### Authentication & accessability
1. **Token**
   1. Distinguish between user OR admin-endpoints (db:admin)
   2. Distinguish between read OR modifiable-endpoints (db:token.scope_read/scope_write)


### Sever Configuration

NOTE: whilst switching to jdbc-next the database must be configuration both in
the config file `config/settings.local.yml` and via environment variables (or cli
arguments).


Set PG environment variables like PGPORT, PGDATABASE, PGUSER, etc.

Create a config/settings.local.yml with content similar like:

    database:
      url: postgresql://localhost:5415/madek?pool=3

### Config-parameters

- `ZERO_BASED_PAGINATION`
  Pagination: used to define `zero-based` OR `one-based pagination`

### Test Configuration

The tests need a rails like configuration:

    cp datalayer/config/database_dev.yml spec/config/database.yml

should be sufficient.

