
Done
--
1. Swagger-UI
   1. Gruppierung & Auth-Interface (not tested)
   2. Beispiel bez. Field-Valdierungen
   3. Generelle Nutzung von [taoensso.timbre]
   4. Keep in mind
      1. Bestimmte Attr müssen unterschiedliche Typen verarbeiten können (uuid/email/char)
   5. Generischer Exception-Handler (sd/parsed_response_exception) um DB-Internas zu handhaben
   6. Use next-helper-fnc in general
   7. Removed java.jdbc-dep
   8. Deps updated


Todo
--
1. Swagger-UI
   1. Testen von Auth-Interface für BasicAuth & Token
   2. Swagger-UI-Fields werden anders definiert & reitit.schema-Validierung muss anscheinend 
      via primärer Middlewarehandler validiert werden. Testen via UI & cUrl
   3. Generelle Verwendung von:
      1. Pagination (zero-based, max 1000)
      2. LevelOfDetail nur für bestimmte Endpoints?
   4. Deklaration von Responses
      1. 500er sollten nicht nötig sein
      2. 500er via 4xx & UserInputValidation vermieden werden
   5. ? POST/PUT/MissingObjectType: Deklaration von Body-Examples in description
   6. Remove dead code? _> "TODO: not in use" 
   7. Duplikate: pagination
   8. Helper um next.jdbc/update-count nochmal zu testen (assert)
   9. MD-Beschreibung in clojure-files


Prio
--
1. Vervollständigen der reitit.schema-Validierung
   - Womöglich fallen hiermit schon zusätzliche Validierungsmassnahmen weg
   - Durch strikte schema-Definitionen sollen Anfrangen mit invaliden Daten in vorhinein Abgefangen werden
   - Überprüfen ob dies bei Middleware-Fetch wirklich funktioniert (fetch by filter-params / ui & curl)
2. Vollständige Schema-Definition sollte die Reduzierung der casts/casting-helpers im Code möglich machen