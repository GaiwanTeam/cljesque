(ns co.gaiwan.cljesque
  "Jesque-based job queue

  Allows enqueuing a simple Clojure function as a job. Job functions receive a
  map of injections as their first argument, passed in at worker startup,
  followed by any arguments that were provided when the job was enqueued.
  "
  (:require
   [clojure.walk :as walk]
   [io.pedestal.log :as log])
  (:import
   (net.greghaines.jesque Config ConfigBuilder Job)
   (net.greghaines.jesque.client Client ClientPoolImpl)
   (net.greghaines.jesque.meta QueueInfo)
   (net.greghaines.jesque.meta.dao QueueInfoDAO WorkerInfoDAO)
   (net.greghaines.jesque.meta.dao.impl QueueInfoDAORedisImpl WorkerInfoDAORedisImpl)
   (net.greghaines.jesque.utils PoolUtils)
   (net.greghaines.jesque.worker JobFactory Worker WorkerImpl)
   (redis.clients.jedis JedisPool)))

(set! *warn-on-reflection* true)

(def ^:dynamic *job*
  "Bound to the job info map (:name, :args) during execution (and middleware execution),
  mainly so Middleware can inspect what they are wrapping."
  nil)

(defn make-config ^Config [redis-host redis-port]
  (.build (doto (ConfigBuilder.)
            (.withHost redis-host)
            (.withPort redis-port))))

(defn jedis-pool ^JedisPool [^Config config]
  (PoolUtils/createJedisPool config))

(defn connect
  "Create a Jesque client with a Redis connection pool"
  ([]
   (connect nil))
  ([{:keys [host port middleware] :or {host "127.0.0.1" port 6379}}]
   (let [config (make-config host port)
         pool   (jedis-pool config)]
     (cond-> {:config     config
              :pool       pool
              :client     (ClientPoolImpl. config pool)}
       middleware
       (assoc :middleware middleware)))))

(defn end-client
  "Stop the client, close connections"
  [{:keys [client]}]
  (.end ^Client client))

(defn job
  "Create a new job, the first argument is the fully qualified name of a Clojure
  var"
  ^Job [var-name args]
  (Job. ^String (str var-name) ^java.util.List args))

(defn enqueue
  "Enqueue a new job"
  [{:keys [client]} queue var-name & args]
  (log/debug :job/enqueing {:var-name var-name :args args})
  (.enqueue ^Client client queue (job var-name args)))

(defn- unmunge-arg [o]
  (cond
    (instance? java.util.ArrayList o)
    (map unmunge-arg o)

    (and (instance? java.util.Map o) (not (map? o)))
    (into {} (map (fn [[k v]]
                    [
                     (if (and (string? k)
                              (.startsWith ^String k ":"))
                       (keyword (subs k 1))
                       k)
                     (unmunge-arg v)])) o)

    :else
    o))

(defn job-info [^Job job]
  {:name (symbol (.getClassName job))
   :args (walk/postwalk unmunge-arg (vec (.getArgs job)))})

(defn- materialize-job ^Callable [injection-map info]
  (let [job-fn (some-> info :name requiring-resolve deref)]
    (if-not job-fn
      (log/error :jesque/job-var-not-found {:var-name (:name info)})
      ;; Return a zero-argument Callable
      #(try
         (log/debug :job/starting info)
         (apply job-fn injection-map (:args info))
         (catch Throwable e
           (log/error :jesque/exception-in-job info :exception e))))))

(defn- injecting-job-factory
  "Jedis Job factory. The job's \"className\" is treated as the fully qualified
  name of a clojure var/function. That functions will get called with the map of
  injections first, followed by the job arguments."
  ^JobFactory [injection-map middleware]
  (reify JobFactory
    (materializeJob [_ ^Job job]
      (println "Got job" job)
      (def jjj job)
      (let [info (job-info jjj)
            job-fn (reduce
                    (fn [job wrap]
                      (wrap job))
                    (materialize-job injection-map info)
                    middleware)]
        #(binding [*job* info]
           (println "before")
           (job-fn)
           (println "after"))))))

(defn worker
  "Create a new Jedis worker instance"
  ^Worker [config queues injections]
  (let [jesque-config (:config config config)]
    (WorkerImpl. jesque-config queues (injecting-job-factory injections (:middleware config)))))

(defn run-worker!
  "Create a new worker and run it on a new thread. Returns [worker thread]"
  [config queues injections]
  (let [worker (worker config queues injections)
        thread (doto (Thread. worker)
                 (.start))]
    [worker thread]))

(defn queue-info-dao ^QueueInfoDAO [{:keys [config pool]}]
  (QueueInfoDAORedisImpl. config pool))

(defn queue-infos [opts]
  (let [dao (queue-info-dao opts)]
    (for [^QueueInfo info (.getQueueInfos dao)
          :let [name (.getName info)
                size (.getSize info)]]
      {:name name
       :size size
       :jobs (for [^Job job (.getJobs (.getQueueInfo dao name 0 size))]
               {:var (symbol (.getClassName job))
                :args (walk/postwalk unmunge-arg (seq (.getArgs job)))})})))

(defn worker-info-dao ^WorkerInfoDAO [{:keys [config pool]}]
  (WorkerInfoDAORedisImpl. config pool))

(defn workers-info
  [client]
  (map bean (.getAllWorkers (worker-info-dao client))))

(defn unregister-worker
  "Unregister worker from Redis"
  [client worker-name]
  (.removeWorker (worker-info-dao client) worker-name))
