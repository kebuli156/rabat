(ns rabat.components.http-client
  (:require
   [clj-http.client :as http-clt]))

(defrecord HttpClient [config])

(defn http-client
  ([config]
   (http-client config {}))
  ([config opts]
   (map->HttpClient (assoc opts :config config))))

(defn request
  [{:keys [config]} http-method uri req]
  (let [caller  (case http-method
                  :get     http-clt/get
                  :head    http-clt/head
                  :post    http-clt/post
                  :put     http-clt/put
                  :delete  http-clt/delete
                  :patch   http-clt/patch
                  :options http-clt/options
                  :copy    http-clt/copy
                  :move    http-clt/move)
        new-uri (str (:host-uri config) uri)]
    (caller new-uri req)))
