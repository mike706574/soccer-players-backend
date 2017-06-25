(ns backend.macros
  (:require [backend.util :as util]
            [com.stuartsierra.component :as component]))

(defmacro with-component
  [constructor config & body]
  `(let [~'component (component/start (~constructor ~config))]
     (try
       ~@body
       (finally (component/stop ~'component)))))

(defmacro with-system
  [system-map & body]
  `(let [~'system (component/start-system ~system-map)]
     (try
       ~@body
       (finally (component/stop-system ~'system)))))

(defmacro unpack-response
  [call & body]
  `(let [~'response ~call
         ~'status (:status ~'response)
         ~'body (:body ~'response)
         ~'text (util/pretty ~'response)]
     ~@body))
