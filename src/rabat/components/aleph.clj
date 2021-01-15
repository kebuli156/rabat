(ns rabat.components.aleph
  (:require
   [aleph.http :as alif]
   [com.stuartsierra.component :as c]
   [rabat.edge.ring :as rbt.edge.ring]))

(defn- get-ring-handler
  [m k]
  (or (get m k)
      (->> (vals m)
           (filter #(satisfies? rbt.edge.ring/RingHandler %))
           (first))))

(defrecord HttpServer []
  c/Lifecycle
  (start [this]
    (if (some? (:server this))
      this
      (let [handler (or (some-> this
                                (get-ring-handler :handler)
                                (rbt.edge.ring/request-handler))
                        (throw (ex-info "missing handler" {})))
            server  (alif/start-server handler (:config this))]
        (assoc this :server server))))
  (stop [this]
    (when-let [server (:server this)]
      (.close server))
    (assoc this :server nil)))

(defn http-server
  [config]
  (map->HttpServer {:config config}))
