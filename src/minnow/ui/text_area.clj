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

(ns minnow.ui.text-area
  (:require 
    [minnow.indent :as indent]
    [minnow.preferences :as prefs]
    [minnow.ui.completion :as completion])
  (:import 
    [java.awt Font]
    [java.io File]
    [org.fife.ui.rsyntaxtextarea RSyntaxTextArea TextEditorPane FileLocation SyntaxConstants]
    [org.fife.ui.rtextarea RTextArea]))

(defn get-highlighting
  [file]
  (let [name (.getName file)]
    (cond
      (.endsWith name ".clj")  SyntaxConstants/SYNTAX_STYLE_CLOJURE
      (.endsWith name ".html")  SyntaxConstants/SYNTAX_STYLE_HTML
      (.endsWith name ".xhtml")  SyntaxConstants/SYNTAX_STYLE_HTML
      (.endsWith name ".css")  SyntaxConstants/SYNTAX_STYLE_CSS
      (.endsWith name ".java")  SyntaxConstants/SYNTAX_STYLE_JAVA
      (.endsWith name ".properties")  SyntaxConstants/SYNTAX_STYLE_PROPERTIES_FILE
      (.endsWith name ".sh")  SyntaxConstants/SYNTAX_STYLE_UNIX_SHELL
      (.endsWith name ".scala")  SyntaxConstants/SYNTAX_STYLE_SCALA)))

(defn set-text-area-defaults
  ([text-area]
    (set-text-area-defaults nil text-area))
  ([file text-area]
  (let [default-font (prefs/get-default-font)
        highlighting (when file (get-highlighting file))]
    (doto text-area
      ;(.setTabsEmulated true) doesn't work, see below
      (.setFont (if default-font default-font (Font. "Monospaced" Font/PLAIN 15)))
      (.setAntiAliasingEnabled true)
      (.setAutoIndentEnabled (not= SyntaxConstants/SYNTAX_STYLE_CLOJURE highlighting))
      (.discardAllEdits)
      (completion/setup-auto-completion))
    (when highlighting
      (.setSyntaxEditingStyle text-area highlighting))
    (indent/setup-auto-indent (.getDocument text-area))
    ; weird method lookup issue for setTabsEmulated, time to hack the matrix
    (-> org.fife.ui.rtextarea.RTextAreaBase
      (.getDeclaredMethod "setTabsEmulated" (into-array Class [Boolean/TYPE]))
      (doto (.setAccessible true))
      (.invoke text-area (into-array Object [true]))))
  text-area))


(defn text-area
  [file]
  (set-text-area-defaults file (RSyntaxTextArea.)))

(defn text-editor-pane
  [file] 
  (set-text-area-defaults file (TextEditorPane.
                                 RTextArea/INSERT_MODE 
                                 true 
                                 (FileLocation/create file))))

