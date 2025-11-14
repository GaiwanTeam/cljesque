# cljesque

<!-- badges -->
[![cljdoc badge](https://cljdoc.org/badge/co.gaiwan/cljesque)](https://cljdoc.org/d/co.gaiwan/cljesque) [![Clojars Project](https://img.shields.io/clojars/v/co.gaiwan/cljesque.svg)](https://clojars.org/co.gaiwan/cljesque)
<!-- /badges -->

Clojure wrapper for Jesque

## Features

- Job queue on top of Redis thanks to Jesque
- Define jobs as simple Clojure functions
- Idiomatic Clojure API

<!-- installation -->
## Installation

To use the latest release, add the following to your `deps.edn` ([Clojure CLI](https://clojure.org/guides/deps_and_cli))

```
co.gaiwan/cljesque {:mvn/version "0.4.11"}
```

or add the following to your `project.clj` ([Leiningen](https://leiningen.org/))

```
[co.gaiwan/cljesque "0.4.11"]
```
<!-- /installation -->

## Rationale

Many projects need some kind of task runner for doing background processes. In
the Ruby world Resque is a very common solution to this, Jesque is a compatible
Java port. This project makes it a bit easier to work with from Clojure.

The Java version expects you to create a new class for each Job type. We hijack
the classname field to instead store the fully qualified name of a Clojure
function (var), which gets resolved (via requiring-resolve) on the worker.

You can spin up any number of worker machines/processes, as long as they can
reach redis, and can load the necessary functions.

Since Jesqueue is compatible with Resqueue, you can use
[Resqueue-web](https://github.com/resque/resque-web) as dashboard UI. 

## Usage

```clojure
(require '[co.gaiwan.cljesque :as q])

;; Set up Redis connection pool and Jesque client
(def client (q/connect))
;; OR
(def client (q/connect {:host "127.0.0.1" :port 6379}))

;; "Job" function
(defn test-fn [inj arg1 arg2]
  (println "TEST" inj arg1 arg2))

;; Schedule jobs
(dotimes [i 10]
  (q/enqueue client "test-queue" `test-fn "first-arg" (str "second-arg-" i)))
  
;; Run a worker
(def injected-values {... db,config etc ...})
(q/run-worker! client ["test-queue"] injected-values)

;; Check what's in the queue(s)
(q/queue-infos client)
```

## Middleware

When calling `q/connect` you can pass a collection of middleware (wrapping)
functions, these will get wrapped on the worker side around the job function.
Useful for things like retries, error handling, etc.

Access the current job info in middleware through `q/*job*`.

## Cleaning up

Workers register themselves in Redis, it is your responsibility to unregister
them before exiting by calling `.end` on the worker, however if e.g. a process
crashes these references can hang around.

You can use `workers-info` to see which workers are registered, and then e.g.
remove the ones with the same hostname but a different PID.

<!-- license -->
## License

Copyright &copy; 2025 Gaiwan GmbH and Contributors

Licensed under the term of the Apache Software License, Version 2.0, see LICENSE.
<!-- /license -->
