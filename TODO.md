# Todos and Notes

## Fragen

### FRAGE: user auth: create token for user

### FRAGE: user create password via admin?

### FRAGE: fix token only auth (method and admin)

### FRAGE: fix session auth (new cookie)

### FRAGE: user self edit?

## TODO datalayer

add cast for collection layout and sorting types

CREATE CAST (varchar AS collection_layout) WITH INOUT AS IMPLICIT;
CREATE CAST (varchar AS collection_sorting) WITH INOUT AS IMPLICIT;

## TODO docu

## TODO tests

### TODO tests for all new features

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

### vocabularies-permissions

### Fix old test failures

- see rspec_resuts.log_20220221.txt

#### TODO fix collection order

#### TODO fix media-entries order

#### TODO fix auth test failures

## TODO User aspects

### fill routes data 

Description, docu:
* hidden query schema options (entry filterByJson)
* Permission names

### rename param full-data to full_data

### TODO usage_terms: get the most recent one ?!? order by updated_at ?


### meta_data try catch logwrite text/json

### workflows - jsonb not hstore conversion problem

### users_workflows

### meta_data_type MediaEntry

### media file: download or show/stream headers

### group

- normal user create group: admin task allow user

### people

- normal user create person: admin task allow user

### users

- edit self

### rdf_classes

### users-workflows

## Admin aspects

### FRAGE: delegations

#### FRAGE: delegations_workflows

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






## TODO
Review:
groups
keywords

media entries
media file
- download or show/stream headers
meta_keys

people
- normal user create person
previews

users
- edit self
rdf_classes
meta_data_type MediaEntry

users-workflows
rdf-classes