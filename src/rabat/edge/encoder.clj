(ns rabat.edge.encoder)

(defprotocol JwtEncoder
  (encode [this claims])
  (decode [this token]))
