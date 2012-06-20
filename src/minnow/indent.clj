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

(ns minnow.indent
  (:require [clojure.string :as s])
  (:use [clojure.core.match :only [match]])
  (:import [javax.swing.text DocumentFilter]))  

(defn find-open-paren
  "The irony of this function is that it fails to indent correctly :)"
  [document offset]
  ;(println "[" document "]")
  (loop [n      (dec offset)
         parens  0
         squares 0
         braces  0
         quoted  false]
    (if (neg? n)
      -1
      (let [c  (.charAt document n)
            p (pos? parens)
            s (pos? squares)
            b (pos? braces)
            q quoted]
        ;(println n c p s b q)
        (match [c  p     s     b     q    ]
               [\" _     _     _     _    ] ; "
                                            (recur (dec n) parens squares braces (not quoted))
               [_  _     _     _     true ] (recur (dec n) parens squares braces quoted)  
               [\) _     _     _     false] (recur (dec n) (inc parens) squares braces quoted)
               [\] _     _     _     false] (recur (dec n) parens (inc squares) braces quoted)
               [\} _     _     _     false] (recur (dec n) parens squares (inc braces) quoted)
               [\( true  _     _     false] (recur (dec n) (dec parens) squares braces quoted)
               [\( false _     _     false] n
               [\[ _     true  _     false] (recur (dec n) parens (dec squares) braces quoted)
               [\[ _     false _     false] n
               [\{ _     _     true  false] (recur (dec n) parens squares (dec braces) quoted)
               [\{ _     _     false false] n
               [_  _     _     _     _    ] (recur (dec n) parens squares braces quoted)
               :else -1)))))

(defn get-indent
  [document offset]
  (loop [n (dec offset)]
    ;(if (>= n 0)
    ;  (println "[" (.charAt document n) "]"))
    (if (>= n 0)
      (if (= \newline (.charAt document n))
        (- offset n)
        (recur (dec n)))
      0)))

(defn calc-indent
  "This is horrible"
  [document offset]
  (let [open-paren-offset (find-open-paren document offset)]
    ;(println "op o: " open-paren-offset)
    (if (>= open-paren-offset 0)
      (let [c (.charAt document open-paren-offset)
            open-paren-indent (get-indent document open-paren-offset)]
        ;(println "op i: " open-paren-indent)
        (condp = c
          \( (if (= open-paren-offset 0) ; this is nasty, fix
               2
               (+ open-paren-indent 1))
          \{ open-paren-indent
          \[ open-paren-indent))
      0)))

(defn reindent-string
  [string]
  (let [lines (map s/triml (s/split string #"\n"))]
    (reduce (fn [result line]
              (let [indent (calc-indent (str result \newline) (+ 1 (count result)))
                    spaces (reduce str (repeat indent " "))]
                (str result \newline spaces line))
              ) lines)))
            
(defn indent-text-if-required
  [text document offset]
  (if (= text "\n")
    (let [indent (calc-indent document offset)]
      (reduce str text (repeat indent " ")))
    text))      

(defn setup-auto-indent
  [document]
  (.setDocumentFilter document
                      (proxy [DocumentFilter] []
                        (insertString [bypass offset string attr]
                          (.insertString bypass offset string attr))
                        (remove [bypass offset len]
                          (.remove bypass offset len))
                        (replace [bypass offset len text attrs]
                          (.replace bypass offset len 
                            (indent-text-if-required text document offset) attrs)))))
                                 
