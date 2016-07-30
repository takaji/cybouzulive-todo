(ns cybouzulive-todo.core
  (:gen-class)
  (:require [oauth.client :as oauth]
            [clj-http.client :as http]
            [clojure.xml :as xml]
            [clojure.string :as str])
  (:import (java.time LocalDateTime ZonedDateTime OffsetDateTime ZoneId ZoneOffset Instant)
           (java.time.format DateTimeFormatter)))

(def consumer-key "2a34a916a41354c02b47912ddb8bf3089d95459")
(def consumer-secret "2b2888982ef9e0bd6e3c839349fdb1e311b72fc")
(def access-token-file "cybouzulive-access-token")
(def endpoint "https://api.cybozulive.com/api/gwTask/V2")

(defn load-access-token []
  (if (.exists (clojure.java.io/file access-token-file))
    (with-open [rdr (clojure.java.io/reader access-token-file)]
      (let [[access-token access-secret] (line-seq rdr)]
        {:access-token-key access-token :access-token-secret access-secret}
        )
      )))


(defn xml-parse [s]
  (xml/parse (java.io.ByteArrayInputStream. (.getBytes s "UTF-8"))))

(defn localdate->str [local-date-str]
  (let [f (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss")
        d (Instant/parse local-date-str)
        z (.atZone d (ZoneId/of "Asia/Tokyo"))]
    (.format z f)))

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


  (print "取得したいグループIDを入力して下さい：")
  (flush)
  (def group-id (read-line))
  (newline)
  
  (def user-params {:group group-id})
;                    :status "NOT_COMPLETED"})
  (def credentials (oauth/credentials consumer
                                      (:access-token-key access-token)
                                      (:access-token-secret access-token)
                                      :GET
                                      endpoint
                                      user-params))

  (def res (http/get endpoint
                     {:query-params (merge credentials user-params)}))

  ;(spit "todo-list.xml" (:body res))
  
  (def csv-header "タイトル,メモ,登録日,期日,担当者,ステータス,優先度")
  (def csv-set #{:title :summary :cbl:published :cbl:when :cbl:who :cbl:task :cblTsk:priority})  
  (def default-csv (apply merge (map #(hash-map % "未設定") csv-set)))
  (def csv-list
    (for [entry (xml-seq (xml-parse (:body res))) :when (= :entry (:tag entry))]
      (let [csv-items (->> (:content entry)
                           (filter #(csv-set (:tag %)))
                           (map (fn [{tag :tag [content] :content attrs :attrs}] (condp = tag
                                                                                   :title {:title (str "\"" content "\"")}
                                                                                   :summary {:summary (str "\"" content "\"")}
                                                                                   :cbl:published {:cbl:published (str "\"" (localdate->str content) "\"")}
                                                                                   :cbl:when {:cbl:when (str "\"" (:endTime attrs) "\"")}
                                                                                   :cbl:who {:cbl:who (str "\"" (:valueString attrs) "\"")}
                                                                                   :cbl:task {:cbl:task (str "\"" (:valueString attrs) "\"")}
                                                                                   :cblTsk:priority {:cblTsk:priority (str "\"" content "\"")}))))]
   
        (->> csv-items
             (apply merge)
             (merge default-csv)
             ((fn [{title :title summary :summary published :cbl:published when :cbl:when who :cbl:who task :cbl:task priority :cblTsk:priority}]
               [title summary published when who task priority]))
             (str/join ","))
             )
      ))

  (def csv (cons csv-header csv-list))

  (def csv-file-name (str "cbl-todo_" (str/replace group-id #":" "_") "_" (.format (java.text.SimpleDateFormat. "yyyyMMddHHmmss") (java.util.Date.)) ".csv"))
  (spit csv-file-name (str/join "\r\n" csv) :encoding "MS932")

  (println "ファイル:" csv-file-name "に出力しました。")
  
  )
