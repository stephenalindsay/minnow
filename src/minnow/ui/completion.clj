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

(ns minnow.ui.completion
  (:import 
    [org.fife.ui.autocomplete AutoCompletion BasicCompletion DefaultCompletionProvider]))


(defn add-publics-to-completion
  [provider namespace]
  (doseq [[sym var] (ns-publics namespace)]
    (let [name (.getName sym)
          {:keys [arglists]} (meta var)]
      (.addCompletion provider (BasicCompletion. provider name (str arglists))))))

(defn setup-auto-completion
  [text-area]
  (let [provider (DefaultCompletionProvider.)]
    (add-publics-to-completion provider 'clojure.core)
    (doto (AutoCompletion. provider)
      (.install text-area))))
