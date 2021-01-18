(ns rabat.components.reitit
  (:require
   [com.stuartsierra.component :as c]
   [reitit.http :as reit.http]
   [reitit.ring :as reit.ring]))

(defrecord RingRoutes [config routes-fn routes]
  c/Lifecycle
  (start [this]
    (assoc this :routes (routes-fn this)))
  (stop [this]
    (assoc this :routes nil)))

(defn ring-routes
  ([routes-fn]
   (ring-routes routes-fn {}))
  ([routes-fn config]
   (map->RingRoutes {:routes-fn routes-fn :config config})))

(defrecord RingOptions [config options-fn options]
  c/Lifecycle
  (start [this]
    (assoc this :options (options-fn this)))
  (stop [this]
    (assoc this :options nil)))

(defn ring-options
  ([options-fn]
   (ring-options options-fn {}))
  ([options-fn config]
   (map->RingOptions {:options-fn options-fn :config config})))

(defrecord RingRouter [kind options router]
  c/Lifecycle
  (start [this]
    (let [routes   (into [] (comp (map val) (keep :routes)) this)
          -options (:options options)
          ctor-f   (case kind
                     :ring reit.ring/router
                     :http reit.http/router)
          -router  (if (empty? -options)
                     (ctor-f routes)
                     (ctor-f routes -options))]
      (assoc this :router -router)))
  (stop [this]
    (assoc this :router nil)))

(defn ring-router
  ([]
   (ring-router :ring))
  ([kind]
   (map->RingRouter {:kind kind})))

(defrecord RingHandler [kind router default-handler options handler]
  c/Lifecycle
  (start [this]
    (let [-router          (or (:router router)
                               (throw (ex-info "missing router" {})))
          -default-handler (or (:handler default-handler)
                               (reit.ring/routes
                                 (reit.ring/create-default-handler)
                                 (reit.ring/redirect-trailing-slash-handler)))
          -options         (:options options)
          ctor-f           (case kind
                             :ring reit.ring/ring-handler
                             :http reit.http/ring-handler)
          -handler         (if (empty? options)
                             (ctor-f -router -default-handler)
                             (ctor-f -router -default-handler -options))]
      (assoc this :handler -handler)))
  (stop [this]
    (assoc this :handler nil)))

(defn ring-handler
  ([]
   (ring-handler :ring))
  ([kind]
   (map->RingHandler {:kind kind})))
