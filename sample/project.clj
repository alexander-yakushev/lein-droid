(defproject sample/sample "0.0.1-SNAPSHOT"
  :description "Sample Android project to test lein-droid plugin."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"

  :global-vars {*warn-on-reflection* true}

  :source-paths ["src/clojure" "src"]
  :java-source-paths ["src/java" "gen"]
  :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]

  ;; The following two definitions are optional. The default
  ;; target-path is "target", but you can change it to whatever you like.
  ;; :target-path "bin"
  ;; :compile-path "bin/classes"

  ;; Uncomment this line if your project doesn't use Clojure. Also
  ;; don't forget to remove respective dependencies.
  ;; :java-only true


  ;; 1.6 is STILL REQUIRED even with rev 19 tools. Otherwise, you get
  ;; java.lang.RuntimeException: Unable to instantiate activity ComponentInfo{sample.sample/sample.sample.SplashActivity}: java.lang.ClassNotFoundException: sample.sample.SplashActivity
  
  :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]

  
  :dependencies [[org.clojure-android/clojure "1.5.1-jb" :use-resources true]
                 [neko/neko "3.0.0"]]
  :profiles {:dev {:dependencies [[org.clojure/tools.nrepl "0.2.3"]
                                  [compliment "0.0.3"]]
                   :android {:aot :all-with-unused}}
             :release {:android {;; Specify the path to your private
                                 ;; keystore and the the alias of the
                                 ;; key you want to sign APKs with.
                                 ;; :keystore-path "/home/user/.android/private.keystore"
                                 ;; :key-alias "mykeyalias"
                                 ;; :sigalg "MD5withRSA"

                                 ;; You can specify these to avoid
                                 ;; entering them for each rebuild,
                                 ;; but generally it's a bad idea.
                                 ;; :keypass "android"
                                 ;; :storepass "android"

                                 :ignore-log-priority [:debug :verbose]
                                 :aot :all}}}

  :android {;; Specify the path to the Android SDK directory either
            ;; here or in your ~/.lein/profiles.clj file.
            ;; :sdk-path "/home/user/path/to/android-sdk/"

            ;; Specify this if your project is a library.
            ;; :library false

            ;; Uncomment this if dexer fails with OutOfMemoryException.
            ;; :force-dex-optimize true

            ;; Options to pass to dx executable, former is for general
            ;; java-related options and later is for 'dex task'-specific options.
            ;; :dex-opts ["-JXmx4096M"]

            ;; Proguard config for "droid create-obfuscated-dex" task.
            ;; :proguard-conf-path "proguard.cfg"
            ;; :proguard-opts ["-printseeds"]

            ;; Uncomment this line to be able to use Google API.
            ;; :use-google-api true

            ;; This option allows you to specify Android support
            ;; libraries you want to use in your application.
            ;; Available versions: "v4", "v7-appcompat",
            ;; "v7-gridlayout", "v7-mediarouter", "v13".
            ;; :support-libraries ["v7-appcompat" "v13"]

            ;; Use this property to add project dependencies.
            ;; :project-dependencies [ "/path/to/library/project" ]

            ;; Sequence of external jars or class folders to include
            ;; into project.
            ;; :external-classes-paths ["path/to/external/jar/file"
            ;;                          "path/to/classfiles/"]

            ;; Sequence of jars, resources from which will be added to
            ;; application package.
            ;; :resource-jars-paths ["path/to/resource/jar"]

            ;; Sequence of native libraries files that will be added
            ;; to application package.
            ;; :native-libraries-paths ["path/to/native/library"]

            ;; Target version affects api used for compilation.
            :target-version :jelly-bean

            ;; Sequence of namespaces that should not be compiled.
            :aot-exclude-ns ["clojure.parallel" "clojure.core.reducers"]

            })
