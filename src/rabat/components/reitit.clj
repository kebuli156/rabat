(ns rabat.components.reitit
  (:require
   [rabat.edge.ring :as rbt.edge.ring]
   [rabat.util.components :as rbt.u.c]
   [reitit.http :as reit.http]
   [reitit.ring :as reit.ring]))

(defrecord RingRoutes []
  rbt.edge.ring/ReititRingRoutes
  (reitit-request-routes [this]
    ((:routes-fn this) this)))

(defn ring-routes
  ([routes-fn]
   (ring-routes routes-fn {}))
  ([routes-fn config]
   (map->RingRoutes {:routes-fn routes-fn :config config})))

(defrecord RingOptions []
  rbt.edge.ring/ReititRingOptions
  (reitit-request-options [this]
    ((:options-fn this) this)))

(defn ring-options
  ([options-fn]
   (ring-options options-fn {}))
  ([options-fn config]
   (map->RingOptions {:options-fn options-fn :config config})))

(defn- get-ring-options
  [m k]
  (or (get m k)
      (rbt.u.c/get-satisfied m rbt.edge.ring/ReititRingOptions)))

(defrecord RingRouter []
  rbt.edge.ring/ReititRingRouter
  (reitit-request-router [this]
    (let [routes  (into []
                       (comp
                         (rbt.u.c/xcollect rbt.edge.ring/ReititRingRoutes)
                         (map rbt.edge.ring/reitit-request-routes))
                       this)
          options (some-> this
                          (get-ring-options :options)
                          (rbt.edge.ring/reitit-request-options))
          ctor-f  (case (:kind this)
                    :ring reit.ring/router
                    :http reit.http/router)]
      (if (empty? options)
        (ctor-f routes)
        (ctor-f routes options)))))

(defn ring-router
  ([]
   (ring-router :ring))
  ([kind]
   (map->RingRouter {:kind kind})))

(defn- get-ring-router
  [m k]
  (or (get m k)
      (rbt.u.c/get-satisfied m rbt.edge.ring/ReititRingRouter)))

(defn- get-ring-handler
  [m k]
  (or (get m k)
      (rbt.u.c/get-satisfied m rbt.edge.ring/RingHandler)))

(defrecord RingHandler []
  rbt.edge.ring/RingHandler
  (request-handler [this]
    (let [router      (or (some-> this
                                  (get-ring-router :router)
                                  (rbt.edge.ring/reitit-request-router))
                          (throw (ex-info "missing router" {})))
          def-handler (or (some-> this
                                  (get-ring-handler :default-handler)
                                  (rbt.edge.ring/request-handler))
                          (reit.ring/routes
                            (reit.ring/create-default-handler)
                            (reit.ring/redirect-trailing-slash-handler)))
          options     (some-> this
                              (get-ring-options :options)
                              (rbt.edge.ring/reitit-request-options))
          ctor-f      (case (:kind this)
                        :ring reit.ring/ring-handler
                        :http reit.http/ring-handler)]
      (if (empty? options)
        (ctor-f router def-handler)
        (ctor-f router def-handler options)))))

(defn ring-handler
  ([]
   (ring-handler :ring))
  ([kind]
   (map->RingHandler {:kind kind})))
