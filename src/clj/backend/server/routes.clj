(ns backend.server.routes
  (:require [backend.football :as football]
            [backend.users :as users]
            [backend.server.authentication :as auth]
            [backend.server.http :refer [with-body
                                        handle-exceptions
                                        body-response
                                        not-acceptable
                                        parsed-body
                                        unsupported-media-type]]
            [clj-time.core :as time]
            [clojure.string :as str]
            [compojure.core :as compojure :refer [ANY DELETE GET PATCH POST PUT]]
            [compojure.route :as route]
            [taoensso.timbre :as log]))

(defn retrieve-players
  [{repo :football-repo} {{id :competition-id term :name} :params :as request}]
  (log/debug (str "Searching for players: " id ", " term))
  (let [pattern (re-pattern (str "(?i)" term))
        matches? (fn [player] (re-find pattern (:nameWithoutDiacritics player)))]
    (handle-exceptions request
      (or (not-acceptable request)
          (let [competition (football/competition repo id)
                players (:players competition)
                body (if-not term
                       players
                       (filter matches? players))]
            (log/debug (str (count body) " matching players found for competition " id " and term \"" term "\"."))
            (Thread/sleep 300)
            (body-response 200 request body))))))

(defn retrieve-teams
  [{repo :football-repo} {{id :competition-id} :params :as request}]
  (handle-exceptions request
    (or (not-acceptable request)
        (let [competition (football/competition repo id)
              teams (:teams competition)]
          (log/debug (str (count teams) " teams found for competition " id "."))
          (Thread/sleep 300)
          (body-response 200 request teams)))))

(defn retrieve-competition
  [{repo :football-repo} {{id :competition-id} :params :as request}]
  (handle-exceptions request
    (or (not-acceptable request)
        (let [competition (football/competition repo id)]
          (log/debug (str "Retrieved competition " id "."))
          (Thread/sleep 300)
          (body-response 200 request competition)))))

(defn create-token
  [{:keys [user-manager authenticator]} request]
  (try
    (or (not-acceptable request #{"text/plain"})
        (with-body [credentials :backend/credentials request]
          (if-let [user (users/authenticate user-manager credentials)]
            {:status 201
             :headers {"Content-Type" "text/plain"}
             :body (auth/token authenticator (:backend/username credentials))}
            {:status 401
             :headers {"Content-Type" "text/plain"}
             :body "Authentication failed."})))
    (catch Exception e
      (log/error e "An exception was thrown while processing a request.")
      {:status 500
       :headers {"Content-Type" "text/plain"}
       :body "An error occurred."})))

(defn routes
  [{:keys [user-manager authenticator] :as deps}]
  (letfn [(unauthenticated [request]
            (when-not (auth/authenticated? authenticator request)
              {:status 401}))]
    (compojure/routes
     (GET "/api/healthcheck" request {:status 200})
     (GET "/api/competition/:competition-id" request (retrieve-competition deps request))
     (GET "/api/competitions/:competition-id/players" request (retrieve-players deps request))
     (GET "/api/competitions/:competition-id/teams" request (retrieve-teams deps request))
     (POST "/api/tokens" request (create-token deps request))
     (route/not-found {:status 404}))))
