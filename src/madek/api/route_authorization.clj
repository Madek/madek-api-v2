(ns madek.api.route-authorization
  "Default-deny guard for the route table: every route must either carry an
  authorization middleware or be listed in `explicitly-unprotected-routes`
  with the reason why that is fine. A new route without either makes the
  application refuse to start (see web/create-app)."
  (:require
   [madek.api.authorization :as authorization]
   [madek.api.resources.media-files.authorization :as media-files.authorization]
   [madek.api.resources.shared.json_query_param_helper :as jqh]
   [madek.api.utils.auth :as auth]
   [reitit.core :as reitit]))

(def authorization-middlewares
  #{auth/wrap-authorize-admin!
    authorization/wrap-authorized-user
    jqh/ring-wrap-authorization-view
    jqh/ring-wrap-authorization-download
    jqh/ring-wrap-authorization-edit-metadata
    jqh/ring-wrap-authorization-edit-permissions
    media-files.authorization/wrap-auth-media-file-metadata-and-previews
    media-files.authorization/wrap-auth-media-file-full_size})

(def ^:private authentication-endpoints "authentication/session endpoints, no resource")
(def ^:private reference-data "public reference data, not bound to a media-resource")
(def ^:private filtered-index "index/handler restricts the result to viewable resources")

(def ^:private custom-url-mapping
  "custom_urls only map an alias to a media-resource id; listing them is public
  today. Restricting them to viewable resources needs a policy decision")

(def explicitly-unprotected-routes
  {[:get "/sign-in"] authentication-endpoints
   [:post "/sign-in"] authentication-endpoints
   [:post "/api-v2/sign-out/"] authentication-endpoints
   [:get "/api-v2/auth-info/"] authentication-endpoints
   [:get "/api-v2/csrf-token/"] authentication-endpoints
   [:get "/api-v2/test-csrf/"] "csrf test endpoint, no data"
   [:post "/api-v2/test-csrf/"] "csrf test endpoint, no data"
   [:put "/api-v2/test-csrf/"] "csrf test endpoint, no data"
   [:delete "/api-v2/test-csrf/"] "csrf test endpoint, no data"
   [:patch "/api-v2/test-csrf/"] "csrf test endpoint, no data"
   [:get "/api-v2/api-docs/openapi.json"] "api documentation"

   [:get "/api-v2/app-settings"] reference-data
   [:get "/api-v2/context-keys/"] reference-data
   [:get "/api-v2/context-keys/:id"] reference-data
   [:get "/api-v2/contexts/"] reference-data
   [:get "/api-v2/contexts/:id"] reference-data
   [:get "/api-v2/keywords/"] reference-data
   [:get "/api-v2/keywords/:id"] reference-data
   [:get "/api-v2/meta-keys/"] "reference data, vocabulary visibility filtered in the handler"
   [:get "/api-v2/meta-keys/:id"] "reference data, vocabulary visibility filtered in the handler"
   [:get "/api-v2/people/:id"] "public people data without internal fields"
   [:get "/api-v2/roles/"] reference-data
   [:get "/api-v2/roles/:id"] reference-data
   [:get "/api-v2/delegations/:id"] "public delegation data: id, name and description only"
   [:get "/api-v2/usage-terms/"] reference-data
   [:get "/api-v2/usage-terms/:id"] reference-data
   [:get "/api-v2/vocabularies/"] "reference data, filtered by vocabulary permissions"
   [:get "/api-v2/vocabularies/:id"] "reference data, filtered by vocabulary permissions"

   [:get "/api-v2/collections/"] filtered-index
   [:get "/api-v2/media-entries/"] filtered-index
   [:get "/api-v2/full_texts/"] filtered-index
   [:get "/api-v2/custom_urls/"] custom-url-mapping
   [:get "/api-v2/custom_urls/:id"] custom-url-mapping
   [:get "/api-v2/collection-media-entry-arcs/"] filtered-index
   [:get "/api-v2/collection-media-entry-arcs/:id"] filtered-index
   [:get "/api-v2/collection-collection-arcs/"] filtered-index
   [:get "/api-v2/collection-collection-arcs/:id"] filtered-index})

(def ^:private http-methods #{:get :post :put :delete :patch})

(defn route-protection
  "Returns for every method of every route of the (reitit.ring) router a map
  of :method, :path and :protected? (authorization middleware present)."
  [router]
  (for [[path _ result] (reitit/compiled-routes router)
        :when (map? result)
        [method endpoint] result
        :when (and (http-methods method) (:data endpoint))]
    {:method method
     :path path
     :protected? (boolean (some authorization-middlewares
                                (-> endpoint :data :middleware)))}))

(defn unprotected-routes
  "Routes without authorization middleware that are not explicitly allowed."
  [router]
  (->> (route-protection router)
       (remove :protected?)
       (remove #(contains? explicitly-unprotected-routes [(:method %) (:path %)]))
       (map (juxt :method :path))))

(defn assert-routes-protected!
  "Throws if a route has neither authorization middleware nor an entry in
  `explicitly-unprotected-routes`. Returns the router."
  [router]
  (when-let [routes (seq (unprotected-routes router))]
    (throw (ex-info (str "Routes without authorization middleware: " (pr-str routes)
                         ". Add one of madek.api.route-authorization/authorization-middlewares"
                         " or list the route in explicitly-unprotected-routes.")
                    {:routes routes})))
  router)
