(ns co.gaiwan.cljesque
  "Jesque-based job queue

  Allows enqueuing a simple Clojure function as a job. Job functions receive a
  map of injections as their first argument, passed in at worker startup,
  followed by any arguments that were provided when the job was enqueued.
  "
  (:require
   [io.pedestal.log :as log])
  (:import
   (net.greghaines.jesque Config ConfigBuilder Job)
   (net.greghaines.jesque.client Client ClientPoolImpl)
   (net.greghaines.jesque.utils PoolUtils)
   (net.greghaines.jesque.worker JobFactory Worker WorkerImpl)))

(set! *warn-on-reflection* true)

(defn make-config ^Config [redis-host redis-port]
  (.build (doto (ConfigBuilder.)
            (.withHost redis-host)
            (.withPort redis-port))))

(defn client
  "Create a Jesque client with a Redis connection pool"
  ^Client [config]
  (ClientPoolImpl. config (PoolUtils/createJedisPool config)))

(defn stop-client
  "Stop the client, close connections"
  [^Client client]
  (.end client))

(defn job
  "Create a new job, the first argument is the fully qualified name of a Clojure
  var"
  ^Job [var-name args]
  (Job. ^String (str var-name) ^java.util.List args))

(defn enqueue
  "Enqueue a new job"
  [^Client client queue var-name & args]
  (.enqueue client queue (job var-name args)))

(defn- materialize-job ^Callable [injection-map ^Job job]
  (let [var-name (symbol (.getClassName job))
        args (vec (.getArgs job))
        job-fn (some-> (requiring-resolve var-name) deref)]
    (if-not job-fn
      (log/error :jesque/job-var-not-found {:var-name var-name})
      ;; Return a zero-argument Callable
      #(try
         (apply job-fn injection-map args)
         (catch Throwable e
           (log/error :jesque/exception-in-job {:var-name var-name
                                                :args args}
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
  (WorkerImpl. config queues (injecting-job-factory injections)))

(defn run-worker!
  "Create a new worker and run it on a new thread. Returns [worker thread]"
  [config queues injections]
  (let [worker (worker config queues injections)
        thread (doto (Thread. worker)
                 (.start))]
    [worker thread]))
