(ns repl-sessions.poke
  (:require
   [co.gaiwan.cljesque :as q])
  (:import
   [net.greghaines.jesque Config Job]
   [net.greghaines.jesque.meta QueueInfo]
   [net.greghaines.jesque.meta.dao QueueInfoDAO]
   [net.greghaines.jesque.meta.dao.impl QueueInfoDAORedisImpl]
   [redis.clients.jedis JedisPool]))

(def client (q/connect))

(defn test-fn [inj arg]
  (println "TEST" inj arg))

(q/enqueue client "test-queue" `test-fn "first-arg")

(q/run-worker! client ["test-queue"] {:inj 123})

(q/queue-infos client)
