
## Fix test failures
### TODO fix collection order
### TODO fix media-entries order
### TODO fix auth test failures

## User aspects read
### TODO get app_settings
### TODO get context
### TODO get context-keys

### TODO create media-file
### TODO create media-entry
### TODO create media-data

## Admin aspects read

## Admin aspects write

### TODO patch app_settings [or full CRUD?]
### TODO create context
### TODO create context-keys
### TODO create vocabulary
### TODO create meta_keys


## setup db
cd datalayer
RAILS_ENV=development ./bin/setup

edit in datalayer/bin/rerun_db_migrations
RAILS_ENV=development rails db:migrate

run
RAILS_ENV=development ./bin/rerun_db_migrations

edit in datalayer/bin/rerun_db_migrations
RAILS_ENV=test rails db:migrate

run
RAILS_ENV=test ./bin/rerun_db_migrations

## drop dbs
bundle exec rails db:drop

## run api
./bin/clj-run

## run clojure tests

clj -X:test :dirs '["test"]'
