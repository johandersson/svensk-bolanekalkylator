(defproject test-clojure "0.1.0-SNAPSHOT"
  :description "Bolånekalkylator"
  :url "https://example.com/FIXME"
  :license {:name "GNU General Public License v3.0"
            :url "https://www.gnu.org/licenses/gpl-3.0.html"}
  :dependencies [[org.clojure/clojure "1.12.2"]]
  :plugins [[dev.weavejester/lein-cljfmt "0.16.5"]]
  :main main
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :repl-options {:init-ns main})
