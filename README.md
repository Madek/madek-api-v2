# The Madek API2

The madek-api2 is a JSON API for Madek.


## API v2 TODOs

* make DB Audits works, including:

    * also there are a lot of `(get-ds)`, at least for writes these must be
        replaced by tx from the wrapper

    * consider to replace all of them with `tx`; that might cost (a little?)
        performance but generally should be safer

* some Files are very large and contain too much functionality, split then
    up in smaller pieces, i.e. per method `get`, `put`, ...


* Schemas:

    * incomplete and too unspecific in many places

    * verbose and hard to maintain: is there a way to automatize this by
      generating it from the db schema?


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
*  ⚠️ WARNING: local tests can fail because of wrong order of results (see terms_for_sorting_shared_context.rb)


### Starting up the Server

    ./bin/clj-run

### Running the Tests

Rspec should be invoked from `./bin/rspec`

        ./bin/rspec ./spec/resources/groups/index_spec.rb:11




### Formatting Code

Use `./bin/cljfmt check` and  `./bin/cljfmt fix`.

From vim you can use `:! ./bin/cljfmt fix %` to format the current file.



### API Docs

Swagger resource documentation http://localhost:3104/api-docs/index.html

### Sever Configuration

NOTE: whilst switching to jdbc-next the database must be configuration both in
the config file `config/settings.local.yml` and via environment variables (or cli
arguments).


Set PG environment variables like PGPORT, PGDATABASE, PGUSER, etc.

Create a config/settings.local.yml with content similar like:

    database:
      url: postgresql://localhost:5415/madek?pool=3


### Test Configuration

The tests need a rails like configuration:

    cp datalayer/config/database_dev.yml spec/config/database.yml

should be sufficient.

