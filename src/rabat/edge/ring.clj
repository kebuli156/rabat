(ns rabat.edge.ring)

(defprotocol RingHandler
  (request-handler [this]))

(defprotocol ReititRingRoutes
  (reitit-request-routes [this]))

(defprotocol ReititRingOptions
  (reitit-request-options [this]))

(defprotocol ReititRingRouter
  (reitit-request-router [this]))
