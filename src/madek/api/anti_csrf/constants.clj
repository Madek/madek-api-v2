(ns madek.api.anti-csrf.constants)

(def USER_SESSION_COOKIE_NAME "madek-session")

(def ANTI_CSRF_TOKEN_COOKIE_NAME "madek-anti-csrf-token")
(def ANTI_CSRF_TOKEN_HEADER_NAME "x-csrf-token")
(def ANTI_CSRF_TOKEN_FORM_PARAM_NAME "csrf-token")

(def PASSWORD_AUTHENTICATION_SYSTEM_ID "password")

(def HTTP_UNSAVE_METHODS #{:delete :patch :post :put})
(def HTTP_SAVE_METHODS #{:get :head :options :trace})
