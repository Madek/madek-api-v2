# Todos and Notes

## TODO docu

## TODO tests

### col-col-arc
### col-me-arc
### cols
### context_keys
### contexts
### custom_urls
### edit_sessions
### io_interfaces
### io_mappings
### favorites
### full_texts
### [groups]
### media_entries
### [media_files]
### meta_data
### meta_keys
### [people]
### permissions
### [previews]
### roles
### usage_terms
### [users]
### static_pages
### vocabularies

### TODO tests for all new features

### Fix test failures

#### TODO fix collection order

#### TODO fix media-entries order

#### TODO fix auth test failures

## TODO User aspects

### fill routes data

### rename param full-data to full_data

### TODO usage_terms get the most recent one ?!?

### auth: create token für user

### fix token only auth (method and admin)

### user self edit?
### user create password via admin?

### col-col-arcs

### roles CRUD

### meta_data try catch

### meta_data roles test and fix

### workflows - jsonb not hstore conversion problem

### users_workflows

## Admin aspects

### delegations

#### delegations_workflows

### TODO vocabulary permissions




## Notes on setup

### setup db (old datalayer)

* copy `datalayer/config/database_developer_example.yml`
* to `datalayer/config/database.yml`
* edit and adapt for db connection.
* run setup script

~~~ setup db
cd datalayer
RAILS_ENV=development ./bin/setup
~~~

* for sql check constraint error, comment out structure.sql in line 1568

~~~ disable constraint
--CONSTRAINT check_allowed_people_subtypes_not_empty_for_meta_datum_people CHECK ((((allowed_people_subtypes IS NOT NULL) AND (COALESCE(array_length(allowed_people_subtypes, 1), 0) > 0)) OR (meta_datum_object_type <> 'MetaDatum::People'::text))),
~~~

* if it creates only the development db
* edit `datalayer/config/database.yml` and exchange

~~~ setup test db
  #database: madek_development
  database: madek_test
~~~

* do migrations

* setup seeds

edit in datalayer/bin/rerun_db_migrations
RAILS_ENV=development rails db:migrate

run
RAILS_ENV=development ./bin/rerun_db_migrations

edit in datalayer/bin/rerun_db_migrations
RAILS_ENV=test rails db:migrate

run
RAILS_ENV=test ./bin/rerun_db_migrations

* drop dbs

~~~ drop dbs
bundle exec rails db:drop
~~~

### run api

./bin/clj-run

### run clojure tests to add two users for testing

clj -X:test :dirs '["test"]'




## DONE

col_col_arcs
col_me_arcs
context_keys
contexts
custom_urls
edit_sessions
fav cols
fav entries
full_texts
io_interfaces
io_mappings
static_pages
usage_terms
workflows

## doing
meta_data
## TODO

groups
keywords
locales
media entries
media file

meta_keys
people
permissions
previews
roles
users
vocabularies
