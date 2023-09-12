# Todos and Notes

## TODO Review (by Alex)

- groups
- media_file
- previews
- meta_keys

## Fragen

### FRAGE: code: wie weiter organisieren

### FRAGE: subprojekte: browser, vendor, docs, docs-source, datalayer

### FRAGE: datalayer: casts: enum-types

### FRAGE: Tests: auth_system? Auth

### FRAGE: Permissions: user auth: create token for user?

### FRAGE: Permissions: user self edit? Welche Felder? Neues Passwort ?

### FRAGE: Permissions: user create password via admin?

### FRAGE: fix token only auth (method and admin): Was wird eh ersetzt?

### FRAGE: fix session auth (new cookie): Was wird eh ersetzt?

###### Andere

### Frage: Permissions: wer darf full-texts anlegen? nur der admin? wird das generiert?

### FRAGE: Oettli: delegations

#### FRAGE: Oettli: delegations_workflows

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
### contexts
### custom_urls
### edit_sessions
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

- hidden query schema options (entry filterByJson)
- Permission names

### rename param full-data to full_data

- admins

### TODO usage_terms: get the most recent one ?!? order by updated_at ?

### workflows - response coercion: jsonb not hstore conversion problem

### updated_at created_at coercion

### meta_data_type MediaEntry

### media file: download or show/stream headers

### group

- normal user create group: admin task allow user

### people

- normal user create person: admin task allow user

### users

- edit self

### confidential_links

### rdf_classes

### sections

### users_workflows

### visualizations

## Admin aspects



## DONE Tests

### user
#### app-settings
#### context-keys
#### keywords
#### roles
#### person

### admin
#### app-settings
#### admins
#### context-keys
#### io-interfaces
#### io-mappings
#### keywords
#### people
#### users
#### groups
