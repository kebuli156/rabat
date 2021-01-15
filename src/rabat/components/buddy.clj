(ns rabat.components.buddy
  (:require
   [buddy.core.keys :as buddy.keys]
   [buddy.sign.jwt :as buddy.jwt]
   [com.stuartsierra.component :as c]
   [rabat.edge.encoder :as rbt.edge.enc]))

(defrecord SHASigner []
  rbt.edge.enc/JwtEncoder
  (encode [this claims]
    (let [config (:config this)]
      (buddy.jwt/sign claims (:secret config) (dissoc config :secret))))
  (decode [this token]
    (let [config (:config this)]
      (buddy.jwt/unsign token (:secret config) (dissoc config :secret)))))

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

(defrecord AsymmetricSigner []
  c/Lifecycle
  (start [this]
    (let [keypair (-> this :config :keypair)
          pubkey  (-> keypair :public-key buddy.keys/public-key)
          privkey (-> keypair :private-key private-key)]
      (assoc this :public-key pubkey :private-key privkey)))
  (stop [this]
    (assoc this :public-key nil :private-key nil))

  rbt.edge.enc/JwtEncoder
  (encode [this claims]
    (let [config  (:config this)
          privkey (:private-key this)]
      (buddy.jwt/sign claims privkey (dissoc config :keypair))))
  (decode [this token]
    (let [config (:config this)
          pubkey (:public-key this)]
      (buddy.jwt/unsign token pubkey (dissoc config :keypair)))))

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
