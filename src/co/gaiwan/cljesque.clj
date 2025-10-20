(ns co.gaiwan.cljesque
  "Jesque-based job queue

  Allows enqueuing a simple Clojure function as a job. Job functions receive a
  map of injections as their first argument, passed in at worker startup,
  followed by any arguments that were provided when the job was enqueued.
  "
  (:require
   [io.pedestal.log :as log]
   [clojure.walk :as walk])
  (:import
   (net.greghaines.jesque Config ConfigBuilder Job)
   (net.greghaines.jesque.client Client ClientPoolImpl)
   (net.greghaines.jesque.meta QueueInfo)
   (net.greghaines.jesque.meta.dao QueueInfoDAO)
   (net.greghaines.jesque.meta.dao.impl QueueInfoDAORedisImpl)
   (net.greghaines.jesque.utils PoolUtils)
   (net.greghaines.jesque.worker JobFactory Worker WorkerImpl)
   (redis.clients.jedis JedisPool)))

(set! *warn-on-reflection* true)

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
  ([{:keys [host port] :or {host "127.0.0.1" port 6379}}]
   (let [config (make-config host port)
         pool   (jedis-pool config)]
     {:config config
      :pool   pool
      :client (ClientPoolImpl. config pool)})))

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
  (.enqueue ^Client client queue (job var-name args)))

(defn- unmunge-arg [o]
  (if (and (instance? java.util.Map o) (not (map? o)))
    (into {} (map (fn [[k v]]
                    [
                     (if (and (string? k)
                              (.startsWith ^String k ":"))
                       (keyword (subs k 1))
                       k)
                     v])) o)
    o))

(defn- materialize-job ^Callable [injection-map ^Job job]
  (let [var-name (symbol (.getClassName job))
        args     (vec (.getArgs job))
        job-fn   (some-> (requiring-resolve var-name) deref)]
    (if-not job-fn
      (log/error :jesque/job-var-not-found {:var-name var-name})
      ;; Return a zero-argument Callable
      #(try
         (apply job-fn injection-map (walk/postwalk unmunge-arg args))
         (catch Throwable e
           (log/error :jesque/exception-in-job {:var-name var-name
                                                :args     args}
                      :exception e))))))

(defn injecting-job-factory
  "Jedis Job factory. The job's \"className\" is treated as the fully qualified
  name of a clojure var/function. That functions will get called with the map of
  injections first, followed by the job arguments."
  ^JobFactory [injection-map]
  (reify JobFactory
    (materializeJob [_ ^Job job]
      (materialize-job injection-map job))))

(defn worker
  "Create a new Jedis worker instance"
  ^Worker [config queues injections]
  (WorkerImpl. (:config config config) queues (injecting-job-factory injections)))

(defn run-worker!
  "Create a new worker and run it on a new thread. Returns [worker thread]"
  [config queues injections]
  (let [config (:config config config)
        worker (worker config queues injections)
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
