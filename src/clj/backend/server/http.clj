(ns backend.server.http
  (:require [backend.message :as message]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [taoensso.timbre :as log]))

(def default-media-type "application/json")
(def primary-media-types #{"application/edn"
                           "application/json"
                           "application/transit+json"
                           "application/transit+msgpack"})

(defn parse-media-type
  [header]
  (str/trim (first (str/split header #";"))))

(defn header [request name] (get-in request [:headers name]))

(defn content-type
  [request]
  (parse-media-type (or (header request "content-type") default-media-type)))

(defn accept
  [request]
  (let [value (header request "accept")]
    (parse-media-type (if (or (not value) (= value "*/*"))
                        default-media-type
                        value))))

(defn unsupported-media-type?
  [request supported-media-types]
  (not (contains? supported-media-types (content-type request))))

(defn unsupported-media-type
  ([request]
   (unsupported-media-type request primary-media-types))
  ([request supported-media-types]
   (when (unsupported-media-type? request supported-media-types)
     {:status 415
      :headers {"Accepts" (str/join ", " supported-media-types)}})))

(defn not-acceptable?
  [request supported-media-types]
  (not (contains? supported-media-types (accept request))))

(defn not-acceptable
  ([request]
   (not-acceptable request primary-media-types))
  ([request supported-media-types]
   (when (not-acceptable? request supported-media-types)
     {:status 406
      :headers {"Consumes" (str/join ", " supported-media-types) }})))

(defn parsed-body
  [request]
  (let [content-type (content-type request)]
    (try
      (message/decode content-type (:body request))
      (catch Exception ex
        (log/error ex (str "Failed to decode " content-type " request body."))))))

(defn response-body "application/transit+msgpack"
  [request body]
  (let [content-type (accept request)]
    (try
      (message/encode content-type body)
      (catch Exception ex
        (throw (ex-info (str "Failed to write " content-type " response body.")
                        {:request request
                         :body body
                         :exception ex}))))))

(defn body-response
  [status request body]
  {:status status
   :headers {"Content-Type" (accept request)}
   :body (response-body request body)})

(defmacro with-body
  [[body-sym body-spec request] & body]
  `(or (unsupported-media-type ~request)
       (let [~body-sym (parsed-body ~request)]
         (if-not ~body-sym
           (body-response 400 ~request {:backend.server/message "Invalid request body representation."})
           (if-let [validation-failure# (s/explain-data ~body-spec ~body-sym)]
             (body-response 400 ~request {:backend.server/message "Invalid request body."
                                          :backend.server/data validation-failure#})
             (do ~@body))))))

(defmacro handle-exceptions
  [request & body]
  `(try
     ~@body
     (catch Exception e#
       (log/error e# "An exception was thrown while processing a request.")
       (body-response 500 ~request {:backend.server/message "An error occurred."}))))
