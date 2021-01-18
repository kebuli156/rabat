(ns rabat.components.aleph
  (:require
   [aleph.http :as alif]
   [com.stuartsierra.component :as c]
   [rabat.edge.logger :refer [info]]))

(defrecord HttpServer [config handler logger server]
  c/Lifecycle
  (start [this]
    (if (some? server)
      this
      (let [-handler (or (:handler handler handler)
                         (throw (ex-info "missing handler" {})))
            _        (info logger ::http-server
                       {:lifecycle :start
                        :config    config})
            -server  (alif/start-server -handler config)]
        (assoc this :server -server))))
  (stop [this]
    (when (some? server)
      (info logger ::http-server
        {:lifecycle :stop})
      (.close server))
    (assoc this :server nil)))

(defn http-server
  [config]
  (map->HttpServer {:config config}))
