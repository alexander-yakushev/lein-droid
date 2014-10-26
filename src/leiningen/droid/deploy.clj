(ns leiningen.droid.deploy
  "Functions and subtasks that install and run the application on the
  device and manage its runtime."
  (:use [leiningen.core.main :only [debug info abort *debug*]]
        [leiningen.droid.manifest :only (get-launcher-activity
                                         get-package-name)]
        [leiningen.droid.utils :only [sh ensure-paths dev-build? append-suffix
                                      prompt-user sdk-binary]]
        [leiningen.droid.compatibility :only (create-repl-port-file)]
        [reply.main :only (launch-nrepl)]))

(defn- device-list
  "Returns the list of currently attached devices."
  [adb-bin]
  (let [output (rest (sh adb-bin "devices"))] ;; Ignore the first line
    (remove nil?
            (map #(let [[_ serial type] (re-find #"([^\t]+)\t([^\t]+)" %)]
                    (when serial
                      {:serial serial, :type type}))
                 output))))

(defn- choose-device
  "If there is only one device attached returns its serial number,
  otherwise prompts user to choose the device to work with. If no
  devices are attached aborts the execution."
  [adb-bin]
  (let [devices (device-list adb-bin)]
    (case (count devices)
      0 (abort "No devices are attached.")
      1 (:serial (first devices))
      (do
        (dotimes [i (count devices)]
          (println (format "%d. %s\t%s" (inc i) (:serial (nth devices i))
                           (:type (nth devices i)))))
        (print (format "Enter the number 1..%d to choose the device: "
                       (count devices)))
        (flush)
        (if-let [answer (try (Integer/parseInt (read-line))
                             (catch Exception ex))]
          (:serial (nth devices (dec answer)))
          (abort "Cannot recognize device number."))))))

(defn get-device-args
  "Returns a list of adb arguments that specify the device adb should be
  working against. Calls `choose-device` if `device-args` parameter is
  nil."
  [adb-bin device-args]
  (or device-args
      (list "-s" (choose-device adb-bin))))

(def ^{:doc "Messages which `adb install` prints as the result."
       :private true}
  adb-responses
  {"Success" :success
   "Failure [INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES]"
   :inconsistent-certificates})

(def ^:private uninstall-prompt
  (str "Certificates of the installed application and the application being "
       "installed mismatch.\nDo you want to uninstall the old application "
       "first? (y/n): "))

;; Since `adb` command always returns exit code zero, we have to
;; manually parse its output to figure out what is going on. This is
;; why this subtask is full of low-level stuff.
(defn install
  "Installs the APK on the only (or specified) device or emulator."
  [{{:keys [out-apk-path manifest-path]} :android :as project}
   & device-args]
  (info "Installing APK...")
  (let [adb-bin (sdk-binary project :adb)
        apk-path (if (dev-build? project)
                   (append-suffix out-apk-path "debug")
                   out-apk-path)
        _ (ensure-paths apk-path)
        device (get-device-args adb-bin device-args)
        output (java.io.StringWriter.)]
    ;; Rebind *out* to get the output `adb` produces.
    (binding [*out* output, *debug* true]
      (sh adb-bin device "install" "-r" apk-path))
    (let [output (str output)
          response (some
                     adb-responses
                     (.split output (System/getProperty "line.separator")))]
      (case response
        :success (debug output)

        :inconsistent-certificates
        (let [resp (prompt-user uninstall-prompt)]
          (if (.equalsIgnoreCase "y" resp)
            (do
              (sh adb-bin device "uninstall" (get-package-name manifest-path))
              (sh adb-bin device "install" apk-path))
            (abort "Cannot proceed with installation.")))

        (do (info output)
            (abort "Abort execution."))))))

(defn run
  "Launches the installed APK on the connected device."
  [{{:keys [manifest-path launch-activity]} :android :as project}
   & device-args]
  (ensure-paths manifest-path)
  (when-let [activity (or launch-activity (get-launcher-activity project))]
    (info "Launching APK...")
    (let [adb-bin (sdk-binary project :adb)
          device (get-device-args adb-bin device-args)]
      (sh adb-bin device "shell" "am" "start" "-n" activity))))

(defn forward-port
  "Binds a port on the local machine to the port on the device.

  This allows to connect to the remote REPL from the current machine."
  [{{:keys [repl-device-port repl-local-port]} :android :as project}
   & device-args]
  (info "Binding device port" repl-device-port
        "to local port" repl-local-port "...")
  (create-repl-port-file project)
  (let [adb-bin (sdk-binary project :adb)
        device (get-device-args adb-bin device-args)]
    (sh adb-bin device "forward"
        (str "tcp:" repl-local-port)
        (str "tcp:" repl-device-port))))

(defn repl
  "Connects to a remote nREPL server on the device using REPLy."
  [{{:keys [repl-local-port]} :android}]
  (launch-nrepl {:attach (str "localhost:" repl-local-port)}))

(defn deploy
  "Metatask. Runs `install, `run`, `forward-port`."
  [project & device-args]
  (let [adb-bin (sdk-binary project :adb)
        device (get-device-args adb-bin device-args)]
    (apply install project device)
    (apply run project device)
    (apply forward-port project device)))
