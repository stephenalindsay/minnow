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

(ns minnow.ui.state)

(def standalone (atom true))
(def current-repl (atom nil))
(def repl-input-area (atom nil))
(def repl-output-area (atom nil))
(def output-tab-pane (atom nil))
(def editor-tab-pane (atom nil))
(def main-frame (atom nil))
(def project-tree (atom nil))
(def main-menu (atom nil))
(def repl-history (atom {:idx -1 :v []}))
(def virtual-dir (atom nil))
(def output-area-to-repl-map (atom {}))
(def debug-repl (atom false))
(def main-split (atom nil))

