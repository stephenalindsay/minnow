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
    [seesaw.keymap :as keymap]    
    [minnow.repl :as repl]
    [minnow.leiningen :as lein]
    [minnow.indent :as indent]
    [minnow.ui.state :as state]
    [minnow.ui.text-area :as ta]
    [minnow.ui.widgets :as widgets])
  (:import
    [java.net ServerSocket]
    [java.io File IOException]
    [java.awt.event KeyEvent InputEvent]))

(declare eval-and-display)

(def running-repls (atom []))

(def repl-input-index (atom {}))

(def set-full-stacktraces (atom true))

(defn roll-repl-history
  [history input-area repl roll-amount]
  (swap! history (fn [{:keys [idx v] :as previous}]
                   (assoc previous :idx (min (- (count v) 1) (max 0 (+ idx roll-amount))))))
  (println "history : " history)
  (let [{:keys [idx v]} @history]
    (.replaceRange input-area "" (get @repl-input-index repl) (.length (.getText input-area)))
    (.append input-area (get v idx))))

(defn update-repl-history!
  [input history]
  (swap! history (fn [{:keys [idx v] :as previous}]
                   (let [n-1 (get v (- (count v) 1))]
                     (if-not (= input n-1)
                       (assoc previous :v (conj v input)
                              :idx (inc (inc idx)))
                       (assoc previous :idx (inc (inc idx)))))))
 (println @history))

(defn show-stacktrace 
  [project-repl output-area]
  (let [{:keys [out ns]} (repl/evaluate-code-in-repl project-repl "(clojure.stacktrace/e)")]
    (when out
      (.append output-area (format "\n%s" out))
      (.append output-area (format "\n%s => " ns)))))

(defn update-repl-output
  [message code project-repl area]
  (println "message : " message)
  (let [{:keys [out err ns value ex root-ex]} message]
    (when out
      (.append area (format "%s" out)))
    (when value
      (.append area (format "%s" value)))
    (when err
      (.append area (format "\n%s" err)))
    (if (and ex @set-full-stacktraces)
      (show-stacktrace project-repl area)
      (.append area (format "\n%s => " ns)))
    (let [len (-> area .getDocument .getLength)]
      (.setCaretPosition area (-> area .getDocument .getLength))
      (swap! repl-input-index assoc project-repl len))))

(defn eval-and-display
  [code project-repl output-area]
  (let [resp (repl/evaluate-code-in-repl project-repl code)]
    (update-repl-output resp code project-repl output-area)))

(defn evaluate-repl-input
  [repl area history]
  (let [toeval (subs (.getText area) (get @repl-input-index repl))]
    (println  "sending : " toeval)
    (update-repl-history! toeval history)
    (future
      (eval-and-display toeval repl area))))

(defn repl-area-listener
  [event repl area history]  
  (if (= InputEvent/CTRL_MASK (.getModifiers event))
    (condp = (.getKeyCode event)
      KeyEvent/VK_UP (roll-repl-history history area repl -1)
      KeyEvent/VK_DOWN (roll-repl-history history area repl 1)
      nil)
    (when (= KeyEvent/VK_ENTER (.getKeyCode event))      
      (let [toeval  (subs (.getText area) (get @repl-input-index repl))
            closed? (= (indent/find-open-paren toeval (count toeval)) -1)]
        (when closed?
          (evaluate-repl-input repl area history))))))

(defn update-ns
  [ns project-repl output-area]
  (eval-and-display (str "(ns " ns ")") project-repl output-area))

(defn setup-edit-stopper
  [document repl]
  (.setDocumentFilter document
                      (proxy [javax.swing.text.DocumentFilter] []
                        (insertString [bypass offset string attr]
                          (when (>= offset (get @repl-input-index repl 0))
                            (.insertString bypass offset string attr)))
                        (remove [bypass offset len]
                          (when (>= offset (get @repl-input-index repl 0))
                            (.remove bypass offset len)))
                        (replace [bypass offset len text attrs]
                          (when (>= offset (get @repl-input-index repl 0))
                            (.replace bypass offset len 
                              (indent/indent-text-if-required text document offset)
                                attrs))))))

(defn start-project-repl
  [project-dir]
  (try
    (let [area         (ta/text-area (File. "minnow.clj"))
          scrollable   (seesaw/scrollable area)
          history      (atom {:idx -1 :v []})
          project-repl (repl/start (.getAbsolutePath project-dir))
          repl-close   #(repl/stop project-repl)
          new-tab      {:title (widgets/button-tab-component
                                  @state/output-tab-pane
                                  (.getName project-dir)
                                  repl-close)
                         :content scrollable
                         :tip (format "%s : %s" 
                                (.getAbsolutePath project-dir) 
                                (:port project-repl))}
          main-ns      (lein/get-main-fn (str project-dir (File/separator) "project.clj"))]
      (setup-edit-stopper (.getDocument area) project-repl)
      (swap! running-repls conj project-repl)
      (swap! state/output-tab-pane #(seesaw/config! % :tabs (conj (:tabs %) new-tab)))
      (.setRightComponent @state/main-split @state/output-tab-pane)          
      (.setDividerLocation @state/main-split 0.65)
      (swap! state/output-area-to-repl-map assoc area project-repl)
      (seesaw/listen area :key-pressed (fn [event]
                                         (repl-area-listener
                                            event 
                                            project-repl 
                                            area 
                                            history)))
      (when @state/output-tab-pane
        (.setSelectedIndex @state/output-tab-pane (.indexOfComponent @state/output-tab-pane scrollable)))
      (update-ns (if main-ns main-ns "user") project-repl area)
      (repl/evaluate-code-in-repl project-repl "(require 'clojure.stacktrace)")
      (swap! repl-input-index assoc project-repl (.length (.getText area)))
      (future 
        (.requestFocusInWindow area))
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

