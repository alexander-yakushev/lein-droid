;; Generally **lein-droid** tries to reuse as much Leiningen code as
;; possible. Compilation subtasks in this namespace just call their
;; Leiningen counterparts.
;;
(ns leiningen.droid.compile
  "This part of the plugin is responsible for the project compilation."
  (:refer-clojure :exclude [compile])
  (:require leiningen.compile leiningen.javac
            [clojure.java.io :as io]
            [leiningen.core.eval :as eval])
  (:use [leiningen.droid.utils :only [get-sdk-android-jar 
                                      get-sdk-google-api-jars
                                      unique-jars
                                      ensure-paths sh dev-build?]]
        [leiningen.core
         [main :only [debug info abort]]
         [classpath :only [get-classpath]]]
        [robert.hooke :only [add-hook]]
        [bultitude.core :only [namespaces-on-classpath]]))

(defn code-gen
  "Generates the R.java file from the resources.

  This task is necessary if you define the UI in XML and also to gain
  access to your strings and images by their ID."
  [{{:keys [sdk-path target-version manifest-path res-path gen-path
            out-res-path]} :android}]
  (info "Generating R.java...")
  (let [aapt-bin (str sdk-path "/platform-tools/aapt")
        android-jar (get-sdk-android-jar sdk-path target-version)
        manifest-file (io/file manifest-path)]
    (ensure-paths sdk-path manifest-path res-path aapt-bin android-jar)
    (.mkdirs (io/file gen-path))
    (.mkdirs (io/file out-res-path))
    (sh aapt-bin "package" "-f" "-m" "--auto-add-overlay"
        "-M" manifest-path
        "-S" res-path
        "-S" out-res-path
        "-I" android-jar
        "-J" gen-path
        "--generate-dependencies")))

;; Before defining actual `compile` functions we have to manually
;; attach Android SDK libraries to the classpath. The reason for this
;; is that Leiningen doesn't handle external dependencies at the high
;; level, that's why we hack `get-classpath` function.

(defn classpath-hook
  "Takes the original `get-classpath` function and the project map,
  extracting the path to the Android SDK and the target version from it.
  Then the path to the actual `android.jar` file is constructed and
  appended to the rest of the classpath list. Also removes all duplicate
  jars from the classpath."
  [f {{:keys [sdk-path target-version]} :android :as project}]
  (let [classpath (f project)
        [jars paths] ((juxt filter remove) #(re-matches #".+\.jar" %) classpath)
        result (conj (concat (unique-jars jars) 
                              paths 
                              (get-sdk-google-api-jars sdk-path target-version))
                     (get-sdk-android-jar sdk-path target-version)
                     
                     (str sdk-path "/tools/support/annotations.jar"))]
    result))

(add-hook #'leiningen.core.classpath/get-classpath #'classpath-hook)

(defn compile-clojure
  "Taken partly from Leiningen source code.

  Compiles Clojure files into .class files.

  If `:aot` project parameter equals `:all` then compiles the
  necessary dependencies. If `:aot` equals `:all-with-unused` then
  compiles all namespaces of the dependencies whether they were
  referenced in the code or not. The latter is useful for the
  REPL-driven development."
  [{{:keys [enable-dynamic-compilation start-nrepl-server]} :android,
    :keys [aot aot-exclude-ns] :as project}]
  (info "Compiling Clojure files...")
  (debug "Project classpath:" (get-classpath project))
  (let [nses
        (case aot
          :all
            (conj (seq (leiningen.compile/stale-namespaces project)) 'neko.init)
          :all-with-unused
            (namespaces-on-classpath :classpath
                                     (map io/file (get-classpath project)))
          (conj (map symbol aot) 'neko.init))
        nses (remove (set (map symbol aot-exclude-ns)) nses)
        dev-build (dev-build? project)]
    (info (format "Build type: %s, dynamic compilation: %s, remote REPL: %s."
                  (if dev-build "debug" "release")
                  (if (or dev-build enable-dynamic-compilation)
                    "enabled" "disabled")
                  (if (or dev-build start-nrepl-server) "enabled" "disabled")))
    (let [form `(neko.init/with-properties
                  [:android-dynamic-compilation ~enable-dynamic-compilation
                   :android-start-nrepl-server ~start-nrepl-server
                   :android-release-build ~(not (dev-build? project))]
                  (doseq [namespace# '~nses]
                    (println "Compiling" namespace#)
                    (clojure.core/compile namespace#)))
          project (update-in project [:prep-tasks]
                             (partial remove #{"compile"}))]
      (.mkdirs (io/file (:compile-path project)))
      (try (eval/eval-in-project project form '(require 'neko.init))
           (info "Compilation succeeded.")
           (catch Exception e
             (abort "Compilation failed."))))))

(defn compile
  "Compiles both Java and Clojure source files."
  [{{:keys [sdk-path]} :android :as project} & args]
  (ensure-paths sdk-path)
  (code-gen project)
  (apply leiningen.javac/javac project args)
  (compile-clojure project))
