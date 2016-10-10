(ns clojure-rest.db-utilsv2
	(:require [amazonica.aws.dynamodbv2 :as ddb 
                                      :only [create-table list-tables]]))
            
(def ^:private cred 
  {:endpoint (str "http://dynamodb." (System/getenv "AWS_REGION") ".amazonaws.com")})

(def ^:private default-values
  "Some default value to provide to dynamodb"
  {:read-capacity-units 2
   :write-capacity-units 2})
  

(defn ^:private toDynamoDBType
  "Translate a custom type (string) to the corresponding dynamodb type"
  [customType]
  (case customType
      ("Binary") "B"
      ("Index" "Date" "Integer" "Float" "Double") "N"
      ("File" "String" "Char") "S"))
     
(defn ^:private create-table
  "Create tables to store an object described in obj in a new dynamodb table"
  [obj]
  (ddb/create-table cred
    (let [obj-keys (:keys (second obj))
          table-name (name (first obj))
          key-schema 
            (let [hash-var [{:attribute-name (-> obj-keys first first name)
                             :key-type "HASH"}]
                range-var (:order-by (-> obj-keys first second))]
              (if range-var 
                (conj hash-var {:attribute-name (name range-var)
                                :key-type "RANGE"})
                          hash-var))
          attribute-definitions 
            (let [infos (conj obj-keys (:data (second obj)))]
              (into [] (for [k (remove nil? (distinct (concat 
                        (keys obj-keys)
                        (map :order-by (vals obj-keys)))))]
                {:attribute-name (name k) :attribute-type (toDynamoDBType (:type (get infos k)))})))
          provisioned-throughput 
            (let [{:keys [read-capacity-units write-capacity-units] 
                   :or {read-capacity-units (:read-capacity-units default-values)
                        write-capacity-units (:write-capacity-units default-values)}} 
                  (:provisioned-throughput (-> obj-keys first second))]
              {:read-capacity-units read-capacity-units
               :write-capacity-units write-capacity-units})
          global-secondary-indexes 
              (reduce (fn [lst index] 
                (let [key-schema (let [hash-var [{:attribute-name (-> index first name)
                                                  :key-type "HASH"}]
                                       range-var (:order-by (second index))]
                                    (if range-var 
                                      (conj hash-var {:attribute-name (name range-var)
                                                      :key-type "RANGE"})
                                      hash-var))
                      provisioned-throughput (let [{:keys [read-capacity-units write-capacity-units] 
                                                    :or {read-capacity-units (:read-capacity-units default-values)
                                                         write-capacity-units (:write-capacity-units default-values)}} 
                                                    (:provisioned-throughput (second index))]
                                                {:read-capacity-units read-capacity-units
                                                 :write-capacity-units write-capacity-units})]
                  (conj lst {:index-name (str "index_" (-> index first name))
                             :key-schema key-schema
                             :projection {:projection-type "ALL"}
                             :provisioned-throughput provisioned-throughput}))) 
                    [] (rest obj-keys))]
    (let [tablemap {:table-name table-name
                    :key-schema key-schema
                    :attribute-definitions attribute-definitions
                    :provisioned-throughput provisioned-throughput}]
    (if-not (empty? global-secondary-indexes)
      (assoc tablemap :global-secondary-indexes global-secondary-indexes)
      tablemap)))))
      
(defn update-db
  "Take a list of object in parameters 
   and update database to support this objects"
  [& args]
  (let [ddb-tables (:table-names (ddb/list-tables cred))]
    (doseq [tab (reduce (fn [ddb-tables my-obj] 
                  (let [tab-name (-> my-obj first first name)] 
                    (if-not (some #{tab-name} ddb-tables)
                      (do
                        (println (str "Create table \"" tab-name "\"."))
                        (-> my-obj first create-table future)
                        ddb-tables)
                      (do (println (str "Update table \"" tab-name "\"."))
                        (filter #(not= % tab-name) ddb-tables)))))
                  ddb-tables args)]
      (println (str "Delete table \"" tab "\".")))))
