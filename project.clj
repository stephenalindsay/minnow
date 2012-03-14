(defproject minnow "0.1.0"
  :description "Minnow: a simple programmer's editor for Clojure"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [seesaw "1.3.0"]
                 [fs "1.0.0"]
                 [org.clojure/core.match "0.2.0-alpha8"]
                 [org.clojars.stevelindsay/tools.nrepl "0.2.0-b2"]
                 [org.clojars.stevelindsay/rsyntaxtextarea "2.0.1"]
                 [org.clojars.stevelindsay/autocomplete "2.0.0"]
                 [fontselector "1.0.0"]]
  :main minnow.ui
  ;:aot :all
  )
