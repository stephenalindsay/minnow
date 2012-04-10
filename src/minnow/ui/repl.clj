;;
;;   Copyright (c) Steve Lindsay, 2012. All rights reserved.
;;
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this 
;;   distribution.
;;
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;
;;   You must not remove this notice, or any other, from this software.
;;

(ns minnow.ui.repl
  (:require 
    [clojure.stacktrace :as st]
    [clojure.tools.nrepl :as nrepl]
    [seesaw.core :as seesaw]
    [minnow.repl :as repl]
    [minnow.leiningen :as lein]
    [minnow.ui.state :as state]
    [minnow.ui.text-area :as ta]
    [minnow.ui.widgets :as widgets])
  (:import
    [java.net ServerSocket]
    [java.io File IOException]
    [java.awt.event KeyEvent InputEvent]))

(def running-repls (atom []))

(defn set-full-stacktraces-in-repl
  [b]
  (doseq [r @running-repls]
    ;(evaluate-code-in-repl r (str "(require 'clojure.tools.nrepl) (set! clojure.tools.nrepl/*print-detail-on-error* " b ")") (StringBuilder.))))
  ))

(defn next-available-port
  [start-port n]
  (let [ss (try
             (ServerSocket. start-port)
             (catch IOException, ioe (println "Port " start-port " unavailable")))]
    (if ss
      (do
        (.setReuseAddress ss true)
        (.close ss)
        start-port)
      (when (> n 0)
        (recur (inc start-port) (dec n))))))

(defn new-repl-idx
  [idx roll-amount item-count]
  (min (- item-count 1) (max 0 (+ idx roll-amount))))

(defn roll-repl-history
  [history input-area roll-amount]
  (swap! history (fn [{:keys [idx v] :as previous}]
                   (assoc previous :idx (min (- (count v) 1) (max 0 (+ idx roll-amount))))))
  (let [{:keys [idx v]} @history]
    (.setText input-area (get v idx))))

(defn update-repl-history!
  [input history]
  (swap! history (fn [{:keys [idx v] :as previous}]
                   (let [n-1 (get v (- (count v) 1))]
                     (if-not (= input n-1)
                       (assoc previous :v (conj v input)
                              :idx (inc idx))
                       previous)))))

(defn evaluate-code-in-repl
  ([project-repl code output-area]
    (evaluate-code-in-repl project-repl code output-area true))
  ([project-repl code output-area verbose]
    (future
      (doseq [m (nrepl/message (:client project-repl)
                               {:op :eval
                                :code code
                                :session (:session project-repl)})]
        (println m)
        (let [{:keys [out err ns value ex root-ex]} m]
          (when err
            (.append output-area (format "\n%s" err)))
          (when ex 
            (future 
              (evaluate-code-in-repl project-repl "(clojure.stacktrace/e)" output-area)))
          (when verbose
            (when out
              (.append output-area (format "\n%s" out)))
            (when value
              (.append output-area (format "\n%s => %s\n%s" ns code value))))
          (.setCaretPosition output-area (-> output-area .getDocument .getLength)))))))

(defn evaluate-repl-input
  [repl input-area output-area history]
  (future
    (let [input (.getText input-area)]      
      (update-repl-history! input history)
      (evaluate-code-in-repl repl input output-area)
      (doto input-area
        (.requestFocusInWindow)
        (.selectAll)))))

(defn repl-area-listener
  [event repl input-area output-area history]
  (when (= InputEvent/CTRL_MASK (.getModifiers event))
    (condp = (.getKeyCode event)
      KeyEvent/VK_ENTER (evaluate-repl-input repl input-area output-area history)
      KeyEvent/VK_UP (roll-repl-history history input-area -1)
      KeyEvent/VK_DOWN (roll-repl-history history input-area 1)
      nil)))

(defn start-project-repl
  [project-dir]
  (try
    (let [output-area  (ta/text-area (java.io.File. "kludge.clj"))
          input-area   (ta/text-area (java.io.File. "kludge.clj"))
          repl-area    (seesaw/top-bottom-split
                         (seesaw/scrollable output-area)
                         input-area
                         :divider-location 3/4)
          history      (atom {:idx -1 :v []})
          project-repl (repl/start (.getAbsolutePath project-dir))
          repl-close   #(repl/stop project-repl)
          new-tab      {:title (widgets/button-tab-component @state/output-tab-pane (.getName project-dir) repl-close)
                        :content repl-area
                        :tip (str (.getAbsolutePath project-dir) " : " (:port project-repl))}
          main-ns      (lein/get-main-fn (lein/read-project-file project-dir))]
        (swap! running-repls conj project-repl)
        (swap! state/output-tab-pane #(seesaw/config! % :tabs (conj (:tabs %) new-tab)))
        (.setRightComponent @state/main-split @state/output-tab-pane)          
        (.setDividerLocation @state/main-split 0.65)
        (swap! state/output-area-to-repl-map assoc output-area project-repl)
        (seesaw/listen input-area :key-pressed (fn [event]
                                                 (repl-area-listener event project-repl input-area output-area history)))
        (when @state/output-tab-pane
          (.setSelectedIndex @state/output-tab-pane (.indexOfComponent @state/output-tab-pane repl-area)))
        (.setText output-area "user => ")
        (.requestFocusInWindow input-area)
        ;(evaluate-code-in-repl project-repl 
        ;  "(require 'clojure.tools.nrepl) (set! clojure.tools.nrepl/*print-detail-on-error* true)" output-area false)
        (when main-ns
          (evaluate-code-in-repl project-repl (str "(ns " main-ns ")") output-area true)
          (evaluate-code-in-repl project-repl "(require 'clojure.stacktrace)" output-area true))      
        project-repl)
      (catch Exception e
        (.printStackTrace e)
        (seesaw/alert
          (str "Couldn't start repl, see exception for details\n\n" (.getMessage e))))))

(defn shutdown-running-repls []
  (swap! running-repls #(doseq [r %]
                                (try
                                  (repl/stop r)
                                  (catch Exception e (.printStackTrace e))))))

