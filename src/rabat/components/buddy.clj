(ns rabat.components.buddy
  (:require
   [buddy.core.keys :as buddy.keys]
   [buddy.sign.jwt :as buddy.jwt]
   [com.stuartsierra.component :as c]
   [rabat.edge.encoder :as rbt.edge.enc]))

(defrecord SHASigner [config]
  rbt.edge.enc/JwtEncoder
  (encode [_ claims]
    (buddy.jwt/sign claims (:secret config) (dissoc config :secret)))
  (decode [_ token]
    (buddy.jwt/unsign token (:secret config) (dissoc config :secret))))

(defn sha-signer
  [config]
  (map->SHASigner {:config config}))

(defn- private-key
  [v]
  (if (map? v)
    (let [path     (:path v)
          password (:password v)]
      (if (some? password)
        (buddy.keys/private-key path password)
        (buddy.keys/private-key path)))
    (buddy.keys/private-key v)))

(defrecord AsymmetricSigner [config public-key private-key]
  c/Lifecycle
  (start [this]
    (let [keypair (:keypair config)
          pubkey  (-> keypair :public-key buddy.keys/public-key)
          privkey (-> keypair :private-key private-key)]
      (assoc this :public-key pubkey :private-key privkey)))
  (stop [this]
    (assoc this :public-key nil :private-key nil))

  rbt.edge.enc/JwtEncoder
  (encode [_ claims]
    (buddy.jwt/sign claims private-key (dissoc config :keypair)))
  (decode [_ token]
    (buddy.jwt/unsign token public-key (dissoc config :keypair))))

(defn asymmetric-signer
  [config]
  (map->AsymmetricSigner {:config config}))

(defn jwt-encoder
  [{:keys [alg] :as config}]
  (cond
    (#{:hs256 :hs512} alg)
    (sha-signer config)

    (#{:es256 :es512 :ps256 :ps512 :rs256 :rs512} alg)
    (asymmetric-signer config)

    :else
    (throw (ex-info "unsupported algorithm" {:alg alg}))))
