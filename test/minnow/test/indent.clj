(ns minnow.test.indent
  (:use [minnow.indent])
  (:use [clojure.test]))

(deftest test-find-open-paren
         (is (= 0 (find-open-paren "(this is a long thing" 15)))
         (is (= -1 (find-open-paren "(this is a long thing)" 22)))
         (is (= -1 (find-open-paren "  (this is a long thing)" 24)))  
         (is (= 2 (find-open-paren "  (this is a long thing" 15)))
         (is (= 6 (find-open-paren "(this (is a long thing" 15)))
         (is (= 2 (find-open-paren "  (this (is a) long thing" 15)))
         (is (= 6 (find-open-paren "(this [is a long thing" 15)))
         (is (= 2 (find-open-paren "  (this [is a] long thing" 15)))
         (is (= 0 (find-open-paren "[this is a long thing" 15)))
         (is (= 2 (find-open-paren "  [this is a long thing" 15)))
         (is (= 6 (find-open-paren "[this (is a long thing" 15)))
         (is (= 2 (find-open-paren "  [this (is a) long thing" 15)))
         (is (= 0 (find-open-paren "{this is a long thing" 15)))
         (is (= 2 (find-open-paren "  {this is a long thing" 15)))
         (is (= 6 (find-open-paren "{this (is a long thing" 15)))
         (is (= 2 (find-open-paren "  {this (is a) long thing" 15)))
         (is (= 2 (find-open-paren "  {this \"(is a\" long thing" 15))))

(def calc-tests 
  [
   ["(defn foo" 2]
   ["(defn foo\n  []" 2]
   ["(defn foo\n  []\n  (let [x 1" 8]
   ["(defn foo\n  []\n  (let [x 1\n        y {:a :b" 11]
   ["(defn foo\n  []\n  (let [x 1]\n" 4]
   ]
  )

(deftest test-calc-indent
         (doseq [[t indent] calc-tests]
           (is (= indent (calc-indent t (count t))))))

(deftest test-reindent-string
         (is (= (reindent-string "(defn foo\n[]\n(let [x 1]\n(println x)))")
                                 "(defn foo\n  []\n  (let [x 1]\n    (println x)))"))
         (is (= (reindent-string "(defn foo\n[]\n(let [x 1\ny 2]\n(println x)))")
                                 "(defn foo\n  []\n  (let [x 1\n        y 2]\n    (println x)))"))
         (is (= (reindent-string "(ns t.core\n(:require t.foo))\n\n(defn test []\n(println \"test : \" (t.foo/bar)))")
                "(ns t.core\n  (:require t.foo))\n\n(defn test []\n  (println \"test : \" (t.foo/bar)))"))
         )



