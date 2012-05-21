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

(declare eval-and-display)

(def running-repls (atom []))

(def set-full-stacktraces (atom true))

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

(defn show-stacktrace 
  [project-repl output-area]
  (let [{:keys [out]} (repl/evaluate-code-in-repl project-repl "(clojure.stacktrace/e)")]
    (when out
      (.append output-area (format "\n%s" out)))))

(defn update-repl-output
  [message code project-repl output-area]
  (println "message : " message)
  (let [{:keys [out err ns value ex root-ex]} message]
    (when value
      (.append output-area (format "\n%s => %s\n%s" ns code value)))
    (when err
      (.append output-area (format "\n%s" err)))
    (when out
      (.append output-area (format "\n%s" out)))
    (when (and ex @set-full-stacktraces)
      (show-stacktrace project-repl output-area))    
    (.setCaretPosition output-area (-> output-area .getDocument .getLength))))

(defn eval-and-display
  [code project-repl output-area]
  (let [resp (repl/evaluate-code-in-repl project-repl code)]
    (update-repl-output resp code project-repl output-area)))

(defn evaluate-repl-input
  [repl input-area output-area history]
  (let [input (.getText input-area)]      
    (println  "sending : " input)
    (update-repl-history! input history)
    (future
      (eval-and-display input repl output-area))))

(defn repl-area-listener
  [event repl input-area output-area history]
  (when (= InputEvent/CTRL_MASK (.getModifiers event))
    (condp = (.getKeyCode event)
      KeyEvent/VK_ENTER (evaluate-repl-input repl input-area output-area history)
      KeyEvent/VK_UP (roll-repl-history history input-area -1)
      KeyEvent/VK_DOWN (roll-repl-history history input-area 1)
      nil)))

(defn update-ns
  [ns project-repl output-area]
  (eval-and-display (str "(ns " ns ")") project-repl output-area))

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
      (when main-ns
        (update-ns main-ns project-repl output-area))
      (repl/evaluate-code-in-repl project-repl "(require 'clojure.stacktrace)")
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

