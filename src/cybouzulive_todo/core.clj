(ns cybouzulive-todo.core
  (:require [oauth.client :as oauth]
            [clj-http.client :as http]))

(def consumer-key "2a34a916a41354c02b47912ddb8bf3089d95459")
(def consumer-secret "2b2888982ef9e0bd6e3c839349fdb1e311b72fc")
(def access-token-file "D:\\cybouzulive-access-token")
                                        ;(def endpoint "https://api.cybozulive.com/api/task/V2")
(def endpoint "https://api.cybozulive.com/api/gwTask/V2")

(defn load-access-token []
  (if (.exists (clojure.java.io/file access-token-file))
    (with-open [rdr (clojure.java.io/reader access-token-file)]
      (let [[access-token access-secret] (line-seq rdr)]
        {:access-token-key access-token :access-token-secret access-secret}
        )
      )))

(defn atom->csv [atom, csv-file]

  )

(defn -main [& args]
  (def consumer (oauth/make-consumer consumer-key
                                     consumer-secret
                                     "https://api.cybozulive.com/oauth/initiate"
                                     "https://api.cybozulive.com/oauth/token"
                                     "https://api.cybozulive.com/oauth/authorize"
                                     :hmac-sha1))

  (def access-token
    (if-let [access-token (load-access-token)]
      access-token
      (do 
          (def request-token (oauth/request-token consumer))


          (def url (oauth/user-approval-uri consumer
                                            (:oauth_token request-token)))

          (println "下記URLより認証コードを取得し、入力してください。")
          (println url)
          (print "認証コード：")
          (flush)
          (def verifier (read-line))
          (newline)
          (def access-token-res (oauth/access-token consumer
                                                    request-token
                                                    verifier))
          
          (spit access-token-file (str (:oauth_token access-token-res) "\n" (:oauth_token_secret access-token-res)))
          {:access-token-key (:oauth_token access-token-res) :access-token-secret (:oauth_token_secret access-token-res)}
          )))

  (def user-params {:group "1:60101"
                    :status "NOT_COMPLETED"})
  (def credentials (oauth/credentials consumer
                                      (:access-token-key access-token)
                                      (:access-token-secret access-token)
                                      :GET
                                      endpoint
                                      user-params))

  (def res (http/get endpoint
                     {:query-params (merge credentials user-params)}))

  (println (:body res))

  )


