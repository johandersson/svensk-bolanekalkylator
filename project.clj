(defproject test-clojure "0.1.0-SNAPSHOT"
  :description "Bolånekalkylator"
  :url "https://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.2"]]
  :plugins [[dev.weavejester/lein-cljfmt "0.16.5"]]
  :main main
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :repl-options {:init-ns main})
