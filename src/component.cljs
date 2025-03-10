(ns progress-bar-v15
  (:require
   [reagent.core :as r]
   [datascript.core :as d]
   [roam.datascript.reactive :as dr]
   [roam.block :as block]
   [blueprintjs.core :as bp-core]))

; Base v14 code with incremental changes for v15 features

;; Circle configuration
(def base-size 11)
(def radius (* 0.75 base-size))
(def viewbox-size (* 2 base-size))
(def center-point base-size)

(defn calculate-coordinates [percentage]
  (let [angle (* (/ percentage 100) (* 2 js/Math.PI))
        x (+ center-point (* radius (js/Math.sin angle)))
        y (- center-point (* radius (js/Math.cos angle)))]
    [x y]))

(defn get-arc-path [percentage]
  (let [[x y] (calculate-coordinates percentage)
        large-arc (if (>= percentage 50) 1 0)
        start-y (- center-point radius)]
    (if (>= percentage 100)
      (str "M " center-point "," start-y " A " radius "," radius " 0 1,1 "
           center-point "," (+ start-y (* 2 radius)) " A " radius "," radius " 0 1,1 "
           center-point "," start-y)
      (str "M " center-point "," start-y " A " radius "," radius " 0 " large-arc ",1 "
           x "," y " L " center-point "," center-point " Z"))))

;; Helper functions
(defn flatten-block [acc block]
  (reduce flatten-block
          (conj acc (dissoc block :block/children))
          (:block/children block)))

(defn find-child-refs [block-uid]
  (flatten-block []
                 @(dr/q '[:find (pull ?e [:block/refs {:block/children ...}]) .
                          :in $ ?uid
                          :where
                          [?e :block/uid ?uid]]
                        block-uid)))

(defn id-title [id]
  (:node/title @(dr/pull '[:node/title] id)))

(defn info-from-id [id]
  (or (:node/title @(dr/pull '[:node/title] id))
      (map id-title
           (map :db/id
                (:block/refs @(dr/pull '[:block/refs] id))))))

(defn count-occurrences [s slist]
  (->> slist
       flatten
       (filter #{s})
       count))

(defn recurse-search [block-uid]
  (->> block-uid
       (find-child-refs)
       (map :block/refs)
       (flatten)
       (map :db/id)
       (map info-from-id)
       (flatten)))

;; NEW: Embed detection and processing (extension to original functions)
(defn contains-embed? [block-string]
  (let [result (and block-string
                   (or (clojure.string/includes? block-string "{{embed:")
                       (clojure.string/includes? block-string "{{[[embed]]:")
                       (clojure.string/includes? block-string "{{embed-children:")
                       (clojure.string/includes? block-string "{{[[embed-children]]:")))
            ]
    result))

(defn extract-uid-from-embed [block-string]
  (when (contains-embed? block-string)
    (let [patterns [#"\{\{embed: *\(\(([^)]+)\)\)\}\}"
                   #"\{\{\[\[embed\]\]: *\(\(([^)]+)\)\)\}\}"
                   #"\{\{embed-children: *\(\(([^)]+)\)\)\}\}"
                   #"\{\{\[\[embed-children\]\]: *\(\(([^)]+)\)\)\}\}"
                   #"\{\{\[\[embed-children\]\]: *\(\(\(\(([^)]+)\)\)\)\)\}\}"] ; Extra pattern for double parentheses
          ;; Try each pattern and collect all matches
          all-matches (mapcat #(re-seq % block-string) patterns)]
      ;; Extract just the UID from each match (second item in match array)
      (mapv second all-matches))))

;; NEW: Enhanced search with embed support
;; Process block with embeds
(defn process-block-with-embeds 
  ([block] (process-block-with-embeds block #{}))
  ([block visited-uids]
   (let [block-uid (:block/uid block)
         block-string (:block/string block)
         has-embed (contains-embed? block-string)
         embed-uids (when (and block-string 
                               (not (contains? visited-uids block-uid))
                               has-embed)
                      (extract-uid-from-embed block-string))
         updated-visited (conj visited-uids block-uid)]
     (concat
      ;; Process this block normally
      (list (dissoc block :block/children))
      ;; Process its children
      (mapcat #(process-block-with-embeds % updated-visited) (:block/children block))
      ;; Process any embedded blocks
      (when (seq embed-uids)
        (mapcat (fn [uid]
                  (when-let [embedded-block 
                             @(dr/q '[:find (pull ?e [:block/uid :block/string :block/refs 
                                                      {:block/children ...}]) .
                                      :in $ ?uid
                                      :where
                                      [?e :block/uid ?uid]]
                                    uid)]
                    ;; Only process if not already visited (prevent cycles)
                    (when-not (contains? updated-visited uid)
                      (process-block-with-embeds embedded-block updated-visited))))
                embed-uids))))))

(defn find-child-refs-with-embeds [block-uid]
  (let [block @(dr/q '[:find (pull ?e [:block/uid :block/string :block/refs {:block/children ...}]) .
                       :in $ ?uid
                       :where
                       [?e :block/uid ?uid]]
                     block-uid)]
    (process-block-with-embeds block)))

(defn recurse-search-with-embeds [block-uid]
  (->> block-uid
       (find-child-refs-with-embeds)
       (keep :block/refs)
       (flatten)
       (keep :db/id)
       (map info-from-id)
       (flatten)))

;; NEW: BlueprintJS component adaptations
(def bp-button (r/adapt-react-class bp-core/Button))
(def bp-popover (r/adapt-react-class bp-core/Popover))
(def bp-menu (r/adapt-react-class bp-core/Menu))
(def bp-menu-item (r/adapt-react-class bp-core/MenuItem))
(def bp-select (r/adapt-react-class bp-core/HTMLSelect))
(def bp-input (r/adapt-react-class bp-core/InputGroup))
(def bp-switch (r/adapt-react-class bp-core/Switch))

;; NEW: Component configuration management
(defn get-component-code-uid [block-string]
  (when block-string
    (let [pattern #"\{\{(?:\[\[)?roam/render(?:\]\])?: *\(\(([^)]+)\)\).*\}\}"
          matches (re-find pattern block-string)]
      (second matches))))

(defn format-render-string [current-string style status-text include-embeds]
  (let [code-block-uid (get-component-code-uid current-string)
        result-string (if code-block-uid
                       (str "{{roam/render: ((" code-block-uid ")) \"" 
                            style "\" \"" 
                            status-text "\"" 
                            (when include-embeds " \"include-embeds\"") 
                            "}}")
                       current-string)]
    result-string))

(defn update-block-string [block-uid style status-text include-embeds]
  (let [current-block @(dr/q '[:find (pull ?e [:block/string]) .
                              :in $ ?uid
                              :where
                              [?e :block/uid ?uid]]
                            block-uid)
        current-string (:block/string current-block)
        new-string (format-render-string current-string style status-text include-embeds)]
    (if (= current-string new-string)
      (-> (block/update
            {:block {:uid block-uid
                     :string new-string}})
                                    ))))

;; NEW: Settings menu
(defn settings-menu [block-uid current-style current-status-text current-include-embeds on-close]
  (let [*style (r/atom current-style)
        *status-text (r/atom current-status-text)
        *include-embeds (r/atom current-include-embeds)]
    (fn []
      [:div.bp3-card.dont-focus-block {:style {:padding "10px" :min-width "250px"}
                                      :on-click (fn [e] (.stopPropagation e))}       
       [:div.setting-group.dont-focus-block {:style {:margin-bottom "15px"}}
        [:label.bp3-label.dont-focus-block "Display Style"]
        [bp-select {:value @*style
                    :class "dont-focus-block"
                    :onChange #(reset! *style (.. % -target -value))
                    :options [{:value "horizontal" :label "Horizontal Bar"}
                              {:value "radial" :label "Radial/Circle"}]}]]
       
       [:div.setting-group.dont-focus-block {:style {:margin-bottom "15px"}}
        [:label.bp3-label.dont-focus-block "Status Text"]
        [bp-input {:value @*status-text
                   :class "dont-focus-block"
                   :onChange #(reset! *status-text (.. % -target -value))
                   :placeholder "Done"}]]
       
       [:div.setting-group.dont-focus-block {:style {:margin-bottom "15px"}}
        [bp-switch {:checked @*include-embeds
                    :class "dont-focus-block"
                    :onChange #(swap! *include-embeds not)
                    :label "Include embedded blocks"}]]
       
       [:div.button-group.dont-focus-block {:style {:display "flex" :justify-content "flex-end" :gap "8px"}}
        [bp-button {:onClick on-close
                    :class "dont-focus-block"
                    :minimal true}
         "Cancel"]
        [bp-button {:intent "primary"
                    :class "dont-focus-block"
                    :onClick (fn [e]
                               (.stopPropagation e)
                               (update-block-string block-uid @*style @*status-text @*include-embeds)
                               (on-close))}
         "Apply"]]])))

;; ENHANCED: Progress bars with added settings button
(defn horizontal-progress-bar [block-uid done total status-text on-settings-click]
  (r/with-let [*hovered? (r/atom false)]
    [:div {:style {:display "flex"
                  :align-items "center"
                  :flex-wrap "wrap"
                  :gap "8px"
                  :margin-left "8px"}
          :on-mouse-enter #(reset! *hovered? true)
          :on-mouse-leave #(reset! *hovered? false)}
     [:div {:style {:flex "1 1 150px"
                    :min-width "150px"
                    :max-width "150px"}} 
      [:progress {:id "file"
                  :name "percent-done"
                  :value done
                  :max total
                  :style {:width "100%"}}]]
     [:div {:style {:flex "0 1 auto"
                    :white-space "nowrap"
                    :display "flex"
                    :align-items "center"
                    :gap "8px"}}
      [:span (str done "/" total " " status-text)]
      [:div {:style {:opacity (if @*hovered? "1" "0")
                     :transition "opacity 0.2s ease-in-out"}}
       [bp-button
        {:icon "cog"
         :class "dont-focus-block"
         :minimal true
         :small true
         :onClick (fn [e] 
                    (.stopPropagation e)
                    (on-settings-click))}]]]]))

(defn circle-progress-bar [block-uid done total status-text on-settings-click]
  (r/with-let [*hovered? (r/atom false)]
    (let [percentage (if (zero? total)
                      0
                      (* (/ done total) 100))]
      [:div.flex.items-center.gap-4
       {:on-mouse-enter #(reset! *hovered? true)
        :on-mouse-leave #(reset! *hovered? false)}
       [:div.relative.inline-flex.items-center.justify-center.progress-circle
        [:svg
         {:width viewbox-size
          :height viewbox-size
          :viewBox (str "0 0 " viewbox-size " " viewbox-size)}
         [:circle
          {:cx center-point
           :cy center-point
           :r radius
           :fill "var(--circle-bg, #eee)"
           :stroke "var(--circle-bg, #eee)"
           :stroke-width "5px"}]
         [:path
          {:d (get-arc-path percentage)
           :fill "var(--circle-fill, #0d8050)"
           :stroke "none"}]]]
       [:div {:style {:display "flex"
                      :align-items "center"
                      :gap "8px"}}
        [:span.text-base
         (str done "/" total " " status-text " - " (int percentage) "%")]
        [:div {:style {:opacity (if @*hovered? "1" "0")
                       :transition "opacity 0.2s ease-in-out"}}
         [bp-button
          {:icon "cog"
           :class "dont-focus-block"
           :minimal true
           :small true
           :onClick (fn [e] 
                      (.stopPropagation e)
                      (on-settings-click))}]]]])))

;; ENHANCED: Main function with new features
(defn main [{:keys [block-uid]} & args]
  (r/with-let [is-running? #(try
                             (.-running js/window.todoProgressBarExtensionData)
                             (catch :default _e
                               false))
               *running? (r/atom (or (is-running?) nil))
               *settings-open? (r/atom false)
               check-interval (js/setInterval #(reset! *running? (is-running?)) 5000)]
    (case @*running?
      nil [:div [:strong "Loading progress bar extension..."]]
      false [:div [:strong {:style {:color "red"}} 
                   "Extension not installed. Please install Todo Progress Bar from Roam Depot."]]
      ;; Enhanced with new features
      (let [style (or (first args) "horizontal")
            status-text (or (second args) "Done")
            include-embeds (= (nth (vec args) 2 nil) "include-embeds")
            search-fn (if include-embeds recurse-search-with-embeds recurse-search)
            todo-refs (search-fn block-uid)
            tasks {:todo (count-occurrences "TODO" todo-refs)
                   :done (count-occurrences "DONE" todo-refs)}
            total (+ (:todo tasks) (:done tasks))]
        [:div.dont-focus-block {:on-click (fn [e] (.stopPropagation e))}
         [bp-popover
          {:isOpen @*settings-open?
           :onClose #(reset! *settings-open? false)
           :class "dont-focus-block"
           :position "auto"
           :content (r/as-element [settings-menu 
                                   block-uid 
                                   style 
                                   status-text 
                                   include-embeds 
                                   #(reset! *settings-open? false)])}
          (if (= style "radial")
            [circle-progress-bar block-uid (:done tasks) total status-text #(reset! *settings-open? true)]
            [horizontal-progress-bar block-uid (:done tasks) total status-text #(reset! *settings-open? true)])]])
      )
    (finally
      (js/clearInterval check-interval))))