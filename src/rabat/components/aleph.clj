(ns rabat.components.aleph
  (:require
   [aleph.http :as alif]
   [com.stuartsierra.component :as c]
   [rabat.edge.logger :as rbt.edge.logger :refer [info]]
   [rabat.edge.ring :as rbt.edge.ring]
   [rabat.util.components :as rbt.u.c]))

(defn- get-ring-handler
  [m k]
  (or (get m k)
      (rbt.u.c/get-satisfied m rbt.edge.ring/RingHandler)))

(defn- get-logger
  [m k]
  (or (get m k)
      (rbt.u.c/get-satisfied m rbt.edge.logger/Logger)))

(defrecord HttpServer []
  c/Lifecycle
  (start [this]
    (if (some? (:server this))
      this
      (let [logger  (get-logger this :logger)
            handler (or (some-> this
                                (get-ring-handler :handler)
                                (rbt.edge.ring/request-handler))
                        (throw (ex-info "missing handler" {})))
            config  (:config this)
            server  (alif/start-server handler config)
            _       (info logger ::http-server
                      {:lifecycle :starting
                       :config    config})]
        (assoc this :server server))))
  (stop [this]
    (when-let [server (:server this)]
      (let [logger (get-logger this :logger)]
        (info logger ::http-server
          {:lifecycle :stopping})
        (.close server)))
    (assoc this :server nil)))

(defn http-server
  [config]
  (map->HttpServer {:config config}))
