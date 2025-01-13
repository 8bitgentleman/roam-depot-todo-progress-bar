(ns progress-bar-v13
  (:require
   [reagent.core :as r]
   [datascript.core :as d]
   [roam.datascript.reactive :as dr]))

; THIS CODEBLOCK IS OVERWRITTEN ON EVERY VERSION UPDATE
; DO NOT MODIFY

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

(defn horizontal-progress-bar [done total]
  [:div {:style {:display "flex"
                 :align-items "center"}}
   [:span 
    [:progress {:id "file"
                :name "percent-done"
                :value done
                :max total
                :style {:margin-left "10px"
                        :margin-right "10px"}}]]
   [:span 
    [:div (str done "/" total " Done")]]])

(defn circle-progress-bar [done total]
  (let [percentage (if (zero? total)
                    0
                    (* (/ done total) 100))]
    [:div.flex.items-center.gap-4
     [:div.relative.inline-flex.items-center.justify-center.progress-circle
      [:svg
       {:width viewbox-size
        :height viewbox-size
        :viewBox (str "0 0 " viewbox-size " " viewbox-size)}
       [:circle
        {:cx center-point
         :cy center-point
         :r radius
         :fill "var(--circle-bg)"
         :stroke "var(--circle-bg)"
         :stroke-width "5px"}]
       [:path
        {:d (get-arc-path percentage)
         :fill "var(--circle-fill)"
         :stroke "none"}]]]
     [:span.text-base
      (str done "/" total " Done - " (int percentage) "%")]]))

(defn main [{:keys [block-uid]} & args]
  (r/with-let [is-running? #(try
                             (.-running js/window.todoProgressBarExtensionData)
                             (catch :default _e
                               false))
               *running? (r/atom (or (is-running?) nil))
               check-interval (js/setInterval #(reset! *running? (is-running?)) 5000)]
    (case @*running?
      nil [:div [:strong "Loading progress bar extension..."]]
      false [:div [:strong {:style {:color "red"}} 
                   "Extension not installed. Please install Todo Progress Bar from Roam Depot."]]
      ; If running is true, then we do your existing logic:
      (let [style (or (first args) "horizontal")
            tasks (r/atom {:todo (count-occurrences "TODO" (recurse-search block-uid))
                          :done (count-occurrences "DONE" (recurse-search block-uid))})
            total (+ (:todo @tasks) (:done @tasks))]
        (if (= style "radial")
          [circle-progress-bar (:done @tasks) total]
          [horizontal-progress-bar (:done @tasks) total])))
    (finally
      (js/clearInterval check-interval))))