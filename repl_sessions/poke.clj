(ns repl-sessions.poke
  (:require
   [clojure.string :as str]
   [co.gaiwan.cljesque :as q]))

(def client (q/connect {:middleware [(fn [job]
                                       (let [start (System/nanoTime)]
                                         (job)
                                         (println "Job" q/*job* "took" (- (System/nanoTime) start)) "ns"))]}))

(defn test-fn [inj arg]
  (def xxx 123)
  (println "TEST" inj arg))

(q/enqueue client "test-queue2" `test-fn "first-arg")

(def w-t
  (q/run-worker! client ["test-queue2"] {:inj 123}))

(.end (first w-t) true)

(q/queue-infos client)
(q/workers-info client)


(let [hostname (.trim (slurp (.getInputStream (.exec (Runtime/getRuntime) "hostname"))))
      pid (.pid (java.lang.ProcessHandle/current))]
  (->> client
       q/workers-info
       (filter (comp #{hostname} :host))
       (remove #(= (str pid) (first (str/split (:pid %) #"-"))))
       (map :name)
       #_(run! (partial q/unregister-worker client))))
