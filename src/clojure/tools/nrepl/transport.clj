
(ns ^{:author "Chas Emerick"}
     clojure.tools.nrepl.transport
  (:require [clojure.tools.nrepl.bencode :as be]
            [clojure.java.io :as io]
            (clojure walk set))
  (:use [clojure.tools.nrepl.misc :only (returning uuid)])
  (:refer-clojure :exclude (send))
  (:import (java.io InputStream OutputStream PushbackInputStream
                    PushbackReader)
           java.net.Socket
           (java.util.concurrent SynchronousQueue LinkedBlockingQueue
                                 BlockingQueue TimeUnit)))

(defprotocol Transport
  "Defines the interface for a wire protocol implementation for use
   with nREPL."
  (recv [this] [this timeout]
    "Reads and returns the next message received.  Will block.
     Should return nil the a message is not available after `timeout`
     ms or if the underlying channel has been closed.")
  (send [this msg] "Sends msg. Implementations should return the transport."))

(deftype FnTransport [recv-fn send-fn close]
  Transport
  (send [this msg] (-> msg clojure.walk/stringify-keys send-fn) this)
  (recv [this] (.recv this Long/MAX_VALUE))
  (recv [this timeout] (clojure.walk/keywordize-keys (recv-fn timeout)))
  java.io.Closeable
  (close [this] (close)))

(defn fn-transport
  "Returns a Transport implementation that delegates its functionality
   to the 2 or 3 functions provided."
  ([read write] (fn-transport read write nil))
  ([read write close]
    (let [read-queue (SynchronousQueue.)]
      (future (while true
                (.put read-queue (read))))
      (FnTransport.
        #(.poll read-queue % TimeUnit/MILLISECONDS)
        write
        close))))

(defn bencode
  "Returns a Transport implementation that serializes messages
   over the given Socket or InputStream/OutputStream using bencode."
  ([^Socket s] (bencode s s s))
  ([in out & [^Socket s]]
    (let [in (PushbackInputStream. (io/input-stream in))
          out (io/output-stream out)]
      (fn-transport
        #(be/read-bencode in)
        #(locking out
           (doto out
             (be/write-bencode %)
             .flush))
        (fn []
          (.close in)
          (.close out)
          (when s (.close s)))))))

(defn tty
  "Returns a Transport implementation suitable for serving an nREPL backend
   via simple in/out readers, as with a tty or telnet connection."
  ([^Socket s] (tty s s s))
  ([in out & [^Socket s]]
    (let [r (PushbackReader. (io/reader in))
          w (io/writer out)
          cns (atom "user")
          prompt (fn [newline?]
                   (when newline? (.write w (int \newline)))
                   (.write w (str @cns "=> ")))
          session-id (atom nil)
          read-msg #(let [code (read r)]
                      (merge {:op "eval" :code [code] :ns @cns :id (str "eval" (uuid))}
                             (when @session-id {:session @session-id})))
          read-seq (atom (cons {:op "clone"} (repeatedly read-msg)))
          write (fn [{:strs [out err value status ns new-session id] :as msg}]
                  (when new-session (reset! session-id new-session))
                  (when ns (reset! cns ns))
                  (doseq [^String x [out err value] :when x]
                    (.write w x))
                  (when (and (= status #{:done}) id (.startsWith ^String id "eval"))
                    (prompt true))
                  (.flush w))
          read #(let [head (promise)]
                  (swap! read-seq (fn [s]
                                     (deliver head (first s))
                                     (rest s)))
                  @head)]
      (fn-transport read write            
        (when s
          (swap! read-seq (partial cons {:session @session-id :op "close"}))
          #(.close s))))))

(defn tty-greeting
  "A greeting fn usable with clojure.tools.nrepl.server/start-server,
   meant to be used in conjunction with Transports returned by the
   `tty` function.

   Usually, Clojure-aware client-side tooling would provide this upon connecting
   to the server, but telnet et al. isn't that."
  [transport]
  (send transport {:out (str ";; Clojure " (clojure-version)
                             \newline "user=> ")}))

(deftype QueueTransport [^BlockingQueue in ^BlockingQueue out]
  clojure.tools.nrepl.transport.Transport
  (send [this msg] (.put out msg) this)
  (recv [this] (.take in))
  (recv [this timeout] (.poll in timeout TimeUnit/MILLISECONDS)))

(defn piped-transports
  "Returns a pair of Transports that read from and write to each other."
  []
  (let [a (LinkedBlockingQueue.)
        b (LinkedBlockingQueue.)]
    [(QueueTransport. a b) (QueueTransport. b a)]))
