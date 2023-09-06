# The Madek API2

The madek-api2 is a JSON API for Madek.

## Development

### Configuration

Create a config/settings.local.yml with content similar like:

    database:
      url: postgresql://localhost:5432/madek_development?pool=3

    services:
      api:
        http:
          port: 3100

This assumes that PUSER and PGPASSWORD environment variables are set. Values
can be submitted as in the following if this is not the case:

    database:
      url: postgresql://localhost:5432/madek_development?pool=3&user=PGUSER&password=PGPASSWORD

### Starting up the Server

Either `lein run` or with-in the REPL  `(-main)`.
Or `./bin/clj-run`

### Running the Tests

When the server is running (!)

    bundle && ./bin/rspec

Note, there is no special environment as in rails.

### Add Dev-Users

Run clojure tests to add two users for development and testing

`clj -X:test :dirs '["test"]'`
